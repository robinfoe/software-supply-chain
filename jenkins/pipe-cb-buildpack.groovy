pipeline {
  agent {
    kubernetes {      
            yaml """
kind: Pod
apiVersion: v1
metadata:
  name: cicdpod
spec:
  containers:
  - name: jnlp
    image: jenkins/jnlp-slave:3.27-1
    args: ['\$(JENKINS_SECRET)', '\$(JENKINS_NAME)']
  - name: kubectl
    image: gcr.io/cloud-builders/kubectl
    imagePullPolicy: IfNotPresent
    command:
      - cat
    tty: true   
  - name: utilities
    image: docker.io/txconsole/utilities
    imagePullPolicy: IfNotPresent
    command:
      - cat
    tty: true         
  restartPolicy: Never
"""
    }
  }

parameters {
    string(name: 'appImageName', defaultValue: 'helloapp', description: 'Application Image Name')
    string(name: 'gitProject', defaultValue: 'gogsuser', description: 'git project for app')
    string(name: 'gitBranch', defaultValue: 'master', description: 'git project for app')
    string(name: 'appType', defaultValue: 'Java', description: 'Type of application - Java / Nodejs')
    //string(name: 'ImageRepoName', defaultValue: 'devopsapps', description: 'App repo address in image registry')
    booleanParam(name: 'PerformDevDeployment', defaultValue: false, description: 'Flag to trigger app deployment')
}  

environment {
    APP_TYPE = "${params.appType}"
    APP_NAME = "${params.appImageName}"
    buildNumber = "${BUILD_NUMBER}"
    GIT_URL = "http://gogs.lab.app.10.16.202.119.nip.io"
    GIT_CRED = "gogscred"
    GIT_REPO = "${params.gitProject}"
    GIT_BRANCH = "${params.gitBranch}"
    NEXUS_VERSION = "nexus3"
    NEXUS_PROTOCOL = "http"
    NEXUS_URL = "proxy.nexus.lab.app.10.16.202.119.nip.io"
    NEXUS_REPOSITORY = "javaapprepo"
    NEXUS_CREDENTIAL_ID = "nexusrepo"
    REGISTRY_URL = "core.harbor.lab.app.10.16.202.119.nip.io"
    REGISTRY_PROJECT = "${params.ImageRepoName}"
    REGISTRY_CRED = ""
    APP_IMAGE_NAME = "${params.appName}"
    APP_GROUP_ID = "vmware"
    APP_BINARY_TYPE = "jar"
    APP_DOMAIN = "lab.app.10.16.202.119.nip.io"
    BUILD_VERSION = "helloapp-build-1"
    BUILD_FIRST_IMAGE = true
    latestImageWithSHA = ""
    tagId = ""

  }

    stages {
        stage('Prepare image resource') {
          steps{
            container('kubectl'){
                script {
                    checkout([$class: 'GitSCM', branches: [[name: "*/${GIT_BRANCH}"]],
                  doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: 
                  [[credentialsId: "${GIT_CRED}", url: "${GIT_URL}/${GIT_REPO}/jenkins.git"]]])

                  sh "ls -la"
                  //get image build resources
                  def imageResource = readYaml file: 'kube-app-image.yml'
                  imageResource.metadata.name = "${APP_NAME}"
                  imageResource.spec.tag = "txconsole/${APP_NAME}"
                  imageResource.spec.source.git.url = "${GIT_URL}/${GIT_REPO}/${APP_NAME}.git"

                  def dockerSecret = readYaml file: 'docker-secret.yml'
                  try{
                    withCredentials([usernamePassword(credentialsId: 'registry-cred', passwordVariable: 'passwd', usernameVariable: 'uname')]) {
                      dockerSecret.stringData.username = "${uname}"
                      dockerSecret.stringData.password = "${passwd}"
                    }
                  } catch(err){
                      echo err.getMessage()
                  }
                  def gitSecret = readYaml file: 'git-secret.yml'
                  try{
                    def objType = "build.pivotal.io/git"
                    gitSecret.metadata.annotations."${objType}" = "${GIT_URL}"
                    withCredentials([usernamePassword(credentialsId: 'gogscred', passwordVariable: 'passwd', usernameVariable: 'uname')]) {
                      gitSecret.stringData.username = "${uname}"
                      gitSecret.stringData.password = "${passwd}"
                    }
                  } catch(err){
                      echo err.getMessage()
                  }
                  def imageBuildSA = readYaml file: 'image-build-sa.yml'
                  def devopsUtilsPvc = readYaml file: 'devopsUtils-pvc.yml'


                  checkout([$class: 'GitSCM', branches: [[name: "*/${GIT_BRANCH}"]], doGenerateSubmoduleConfigurations: false, 
                  extensions: [[$class: 'LocalBranch', localBranch: "${GIT_BRANCH}"], [$class: 'CleanBeforeCheckout'], 
                  [$class: 'CloneOption', depth: 1, noTags: false, reference: '', shallow: true, timeout: 10]], submoduleCfg: [], 
                  userRemoteConfigs: [[credentialsId: "${GIT_CRED}", url: "${GIT_URL}/${GIT_REPO}/${APP_NAME}.git"]]])

                  def latestCommitTag = sh(returnStdout: true, script: 'git show-ref --heads --hash').trim()
                  
                  imageResource.spec.source.git.revision = "${latestCommitTag}"
                  writeYaml file: 'appImgResource', data: imageResource
                  sh 'cat appImgResource'

                  // check for the presence of app environment
                  def nsStatus = sh(returnStatus: true, script: "kubectl get ns ${APP_NAME}-env")
                    if (nsStatus == 0) {
                        sh "kubectl get ClusterBuilder"
                        //check if image resource exist
                        def imageResStatus = sh(returnStatus: true, script: "kubectl get image ${APP_NAME} -n ${APP_NAME}-env")
                        if(imageResStatus == 0){
                          echo "NOTE: Image resource => ${APP_NAME} exists, app-image was built with kpack in past."
                          sh "kubectl get all,image,builds -n ${APP_NAME}-env"
                          BUILD_FIRST_IMAGE = false

                          // TODO check for a build with same revision successfully triggered in past.

                          //apply image resource
                          try{
                            sh "kubectl apply -f appImgResource -n ${APP_NAME}-env"
                          } catch(err){
                              echo err.getMessage()
                          }

                        } else {
                          echo "NOTE: Since image resource => ${APP_NAME} DO NOT exist, app-image was built without kpack in past."
                        }
 
                    } else {
                      //create app namespace
                      sh "kubectl create ns ${APP_NAME}-env"
                      sleep time: 500, unit: 'MILLISECONDS'

                      echo "NOTE: Image resource => ${APP_NAME} DO NOT exist"
                      //create docker-secret
                      try{
                        writeYaml file: 'dockerSecret', data: dockerSecret
                        sh 'cat dockerSecret'
                        sh "kubectl apply -f dockerSecret -n ${APP_NAME}-env"
                      } catch(err){
                          echo err.getMessage()
                      }

                      //create git-secret
                      try{
                        writeYaml file: 'gitSecret', data: gitSecret
                        sh 'cat gitSecret'
                        sh "kubectl apply -f gitSecret -n ${APP_NAME}-env"
                      } catch(err){
                          echo err.getMessage()
                      }

                      //create service-account
                      try{
                        writeYaml file: 'imageBuildSA', data: imageBuildSA
                        sh 'cat imageBuildSA'
                        sh "kubectl apply -f imageBuildSA -n ${APP_NAME}-env"
                      } catch(err){
                          echo err.getMessage()
                      }

                      //apply image resource
                      try{
                        sh "kubectl apply -f appImgResource -n ${APP_NAME}-env"
                      } catch(err){
                          echo err.getMessage()
                      }
                      
                    }
			          
		            }
            }
          }
        } // end stage


        // stage('Monitor build progress') {
        //     steps {
		    //     script {
        //             container("utilities"){
        //                 try{ 
        //                     sh "timeout -s KILL 15s logs -image ${APP_NAME} -namespace ${APP_NAME}-env "
        //                     echo "this is done"
        //                 } catch(err){
        //                     echo err.getMessage()
        //                 }
		    //         }
		    //     }
        //     }
        // }	 //end stage

        stage('Monitor build progress') {
            steps {
		        script {
                    container("utilities"){
                        
                            // TODO : build check logic
                            def imageBuildLogs = null
                            retry(10) {
                                try {
                                    imageBuildLogs  = sh(returnStdout: true, script: "timeout -s KILL 5s logs -image helloapp -namespace helloapp-env")
                                
                                } catch(err){
                                    echo err.getMessage()
                                    //currentBuild.result = 'FAILURE'
                                    findText(textFinders: [textFinder(alsoCheckConsoleOutput: true, buildResult: 'SUCCESS', regexp: 'Build successful')])
                                    //findText(textFinders: [textFinder(alsoCheckConsoleOutput: true, regexp: 'Build successful')])
                                    if("${currentBuild.result}" != "null"){
                                        sh "exit 1"
                                    } else {
                                         currentBuild.result = 'SUCCESS'
                                    }
                                }
                                echo "RESULT: ${currentBuild.result}"
                                //writeFile encoding: 'UTF-8', file: 'imageLogData', text: "${imageBuildLogs}"
                                echo "this is the file log output"
                                
                                //sh "tail imageLogData"
                            }
                          
		            }
		        }
            }
        }	 //end stage

        stage('verify build and images') {
            steps {
              script {
                      container("kubectl"){
                          echo "NOTE: Image build is completed."
                            // check status for the latest build version
                            def builds = sh(returnStdout: true, script: "kubectl get builds  -o jsonpath='{.items[*].metadata.name}' -n ${APP_NAME}-env").trim()
                            buildList = builds.split()
                            echo "$buildList"
                            buildCount = buildList.size()
                            echo "buildCount:  $buildCount"
                            buildCount = buildCount - 1
                            
                            latestBuildName = buildList[buildCount]
                            echo "$latestBuildName"

                            def buildStatus = sh(returnStdout: true, script: "kubectl get builds  -o jsonpath=\\'{.items[\\?\\(\\@.metadata.name==\\\"${latestBuildName}\\\"\\)].status.conditions[*].type}\\' -n ${APP_NAME}-env").trim()
                            echo "Status of latestBuild : ${latestBuildName} =>  $buildStatus" 
                            if(buildStatus.contains("Succeeded")) {
                                // get image id
                                latestImageWithSHA = sh(returnStdout: true, script: "kubectl get builds  -o jsonpath=\\'{.items[\\?\\(\\@.metadata.name==\\\"${latestBuildName}\\\"\\)].status.latestImage}\\' -n ${APP_NAME}-env").trim()
                                // 'index.docker.io/txconsole/helloapp@sha256:90e440ab7e5a78790408def94de44f2768654eaab394ca836b214a39347efd7d'
                                echo "$latestImageWithSHA"

                                //get the sha code
                                def latestImageWithTAG = sh(returnStdout: true, script: "kubectl get builds  -o jsonpath=\\'{.items[\\?\\(\\@.metadata.name==\\\"${latestBuildName}\\\"\\)].spec.tags[1]}\\' -n ${APP_NAME}-env").trim()  
                                // index.docker.io/txconsole/helloapp:b3.20200707.043933
                                echo "$latestImageWithTAG"
                                
                                tagId = latestImageWithTAG.split(":")[1].reverse().drop(1).reverse()
                                echo "Image Tag =>  $tagId"
                                
                            } else {
                                echo "Status of latestBuild : ${latestBuildName} =>  ${buildStatus} , thus no deployment would be triggered."
                            }
                          

                  }
              }
            }
        }	 //end stage

        stage ('Starting appDeploy job') {
          steps {
            script {
             if("${params.PerformDevDeployment}" == "true") {

                  build job: 'pipe-cd', 
                  parameters: [
                    string(name: 'appName', value: "${APP_NAME}"), 
                    string(name: 'appImageVersion', value: "${tagId}"), 
                    string(name: 'appURL', value: "${APP_NAME}.${APP_DOMAIN}"),
                    string(name: 'gitProject', value: "${GIT_REPO}"), 
                    string(name: 'gitBranch', value: "${GIT_BRANCH}"),
                    string(name: 'chooseCluster', value: "development"),
                   // choice(name: 'chooseCluster', choices: ['development']),
                    string(name: 'appNamespace', value: "${APP_NAME}-env"),
                    string(name: 'ImageRepoName', value: "${REGISTRY_PROJECT}")
                  ]

              }
            }
          }
        } 

        

   }

}

