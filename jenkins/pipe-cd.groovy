
def globalVar = [:]
def modules = [:]

pipeline {
  agent {
    kubernetes {      
            yaml """
kind: Pod
apiVersion: v1
metadata:
  name: ivy-cd
spec:
  serviceAccountName: jenkins
  containers:
  - name: jnlp
    image: jenkins/jnlp-slave:3.27-1
    args: ['\$(JENKINS_SECRET)', '\$(JENKINS_NAME)']

  - name: kaniko
    image: gcr.io/kaniko-project/executor:debug-v0.23.0
    imagePullPolicy: IfNotPresent
    command:  
    - /busybox/cat
    tty: true
    volumeMounts:
      - name: docker-config
        mountPath: /kaniko/.docker

  - name: kubectl
    image: gcr.io/cloud-builders/kubectl
    imagePullPolicy: IfNotPresent
    command:
      - cat
    tty: true    

  volumes:
  - name: docker-config
    configMap:
      name: docker-config    
  restartPolicy: Never
"""
    }
  }

parameters {

    // GIT
    string(name: 'gitURL', defaultValue: 'https://github.com/robinfoe/bookstore-ms.git', description: 'Git Clone URL')
    string(name: 'gitBranch', defaultValue: 'master', description: 'git project for app')
    string(name: 'gitAppFolder', defaultValue: 'book', description: 'Application Root Folder, leave blank pom.xml is in Root directory')

    string(name: 'kubeResourceFolder', defaultValue: 'kubernetes', description: 'Kubernetes resource folder in the source code')

    // APP
    string(name: 'appName', defaultValue: 'book', description: 'Application Name')
    string(name: 'imagePrefix', defaultValue: 'docker.io/robinfoe', description: 'Image prefix ( Default point to dockerhub)')
    string(name: 'buildNumber', defaultValue: '67', description: 'Build Number, propagated from parent task')

    string(name: 'appUrlSuffix', defaultValue: '.app.pipeline.ivy.tanzu-no.de', description: 'URL Suffix')
    

    string(name: 'namespacePrefix', defaultValue: 'tanzu-app-pipeline', description: 'Namespace prefix for application')
    
    choice(name: 'deployEnvironment', choices: ['sit', 'prod'],  description: 'Choose an environment to deploy app')



}  


  stages {

    stage('Checkout SourceCode') {
        steps{
            script {
              modules.helper = load("${env.WORKSPACE}/jenkins/utility/helper.groovy")

              checkout(
                  [
                    $class: 'GitSCM', 
                    branches: [[name: "*/${params.gitBranch}"]],

                    doGenerateSubmoduleConfigurations: false, extensions: [], 
                      submoduleCfg: [], 
                      userRemoteConfigs: [
                          [
                            url: "${params.gitURL}"
                          ]
                      ]
                  ]
              )
            
          }
        }
    } //end stag
     

    stage('Generate Kubernetes Resources'){
        steps{
          script{
            
            modules.helper.generateKubeResource( params.gitAppFolder, params.kubeResourceFolder,
                                  params.imagePrefix, params.appName, 
                                  params.buildNumber, params.appUrlSuffix,
                                  params.deployEnvironment )
          }
        }
    } //stage ends 

    stage('Attemp to re-tag image'){
        when { expression { !(isSIT(params.deployEnvironment)) } }

        steps{
          container('kaniko'){

            script{
              def fromImage = generateImageTag(params.imagePrefix, params.appName, params.buildNumber, "sit")
              def toImage = generateImageTag(params.imagePrefix, params.appName, params.buildNumber, params.deployEnvironment)
              modules.helper.retagImage(fromImage , toImage)
            }

          }
         
        }
    } //stage ends 

    stage('Apply Kube Resources'){
        steps{
          container('kubectl'){
              script {

                def namespace = generateNamespace(params.namespacePrefix , params.deployEnvironment)
                def kubeConstructFolder = params.gitAppFolder+ '/'+ params.kubeResourceFolder
                modules.helper.deployKubeResource(kubeConstructFolder, namespace, params.appName)

              }
          } //container ends
        }
    } //stage ends 


  } 
}







