
def modules = [:]
def jobVar = [:]


pipeline {
  agent { //maven:3.3.9-jdk-8-alpine
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

"""
    }
  }

parameters {

    // APP
    string(name: 'appName', defaultValue: 'book', description: 'Application Name')
    choice(name: 'appType', choices: ['Java', 'Nodejs'], description: 'Type of application - Java / Nodejs')
    choice(name: 'typeOfBuild', choices: ['Dockerfile', 'Buildpack'], description: 'Choose the app build type,  default -  Dockerfile')

    //IMAGE_PREFIX
    string(name: 'imagePrefix', defaultValue: 'robinfoe', description: 'Image prefix ( Default point to dockerhub)')

    // GIT
    string(name: 'gitURL', defaultValue: 'https://github.com/robinfoe/bookstore-ms.git', description: 'Git Clone URL')
    string(name: 'gitBranch', defaultValue: 'master', description: 'git project for app')
    string(name: 'gitAppFolder', defaultValue: 'book', description: 'Application Root Folder, leave blank pom.xml is in Root directory')

    // string(name: 'sonarUrl', defaultValue: 'http://sonarqube.pipeline.tanzu-no.de', description: 'Sonarqube URL')
    string(name: 'mavenProxyFile', defaultValue: '/tmp/m2/ivy-settings.xml', description: 'Location to settings.xml')
    
    
    booleanParam(name: 'performDependencyCheck', defaultValue: true, description: 'Perform app dependency checks ?')
    booleanParam(name: 'performCodeQualityCheck', defaultValue: true, description: 'Perform app code Quality checks ?')
    // booleanParam(name: 'performImageBuild', defaultValue: false, description: 'Flag to trigger app image build')
    // booleanParam(name: 'performDevDeployment', defaultValue: false, description: 'Flag to trigger app deployment')
}  


  stages {

    stage('init... ') {
        steps{
            script {
              modules.helper = load("${env.WORKSPACE}/jenkins/utility/helper.groovy")
              
          }
        }
    } //end stag

    
    stage('Stage - CI') {          
      
      steps {
        script {
          def task = build (
                      job: 'pipe-ci', 
                      parameters: [
                        string(name: 'appName', value: "${params.appName}"),
                        string(name: 'buildNumber', value: "${BUILD_NUMBER}"),
                        string(name: 'gitURL', value: "${params.gitURL}"),
                        string(name: 'gitBranch', value: "${params.gitBranch}"),
                        string(name: 'gitAppFolder', value: "${params.gitAppFolder}"),
                        string(name: 'mavenProxyFile', value: "${params.mavenProxyFile}"),
                        
                        booleanParam(name: 'performDependencyCheck', value: "${params.performDependencyCheck}"),
                        booleanParam(name: 'performCodeQualityCheck', value: "${params.performCodeQualityCheck}"),
                      ]
                    )

          jobVar.appCoordinate = task.getBuildVariables().get('APP_COORDINATE')


        }
      }
    }  //end stage


    stage('Stage - CB') {          
      
      steps {
        script {

          echo jobVar.appCoordinate

          // def task = build (
          //             job: 'pipe-cb-dockerfile', 
          //             parameters: [
          //               string(name: 'appName', value: "${params.appName}"),
          //               string(name: 'buildNumber', value: "${BUILD_NUMBER}"),
          //               string(name: 'gitURL', value: "${params.gitURL}"),
          //               string(name: 'gitBranch', value: "${params.gitBranch}"),
          //               string(name: 'gitAppFolder', value: "${params.gitAppFolder}"),
          //               string(name: 'sonarUrl', value: "${params.sonarUrl}"),
          //               string(name: 'mavenProxyFile', value: "${params.mavenProxyFile}"),
          //               booleanParam(name: 'performDependencyCheck', value: "${params.performDependencyCheck}"),
          //               booleanParam(name: 'performCodeQualityCheck', value: "${params.performCodeQualityCheck}"),

          //             ]
          //           )


          // jobVar.appCoordinate = task.getBuildVariables().get('APP_COORDINATE')


        }
      }
    }  //end stage


    // stage('Stage - CB ') {
    //   when {environment name: "PERFORM_CODE_CHK", value: "true"}

    //   steps {
    //     script {
    //         container("maven"){
    //             runCodeQualityCheck( GIT_APP_FOLDER , PROXY_SETTINGS , APP_NAME, buildNumber )
    //         }
    //     }
    //   }
    // } //end stage 



  }
}








