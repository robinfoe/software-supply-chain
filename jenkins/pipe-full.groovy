
def modules = [:]



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

    // GIT
    string(name: 'gitURL', defaultValue: 'https://github.com/robinfoe/bookstore-ms.git', description: 'Git Clone URL')
    string(name: 'gitBranch', defaultValue: 'master', description: 'git project for app')
    string(name: 'gitAppFolder', defaultValue: 'book', description: 'Application Root Folder, leave blank pom.xml is in Root directory')

    string(name: 'sonarUrl', defaultValue: 'http://sonarqube.pipeline.tanzu-no.de', description: 'Sonarqube URL')
    string(name: 'mavenProxyFile', defaultValue: '/tmp/m2/tmp-nexus-proxy.xml', description: 'Location to settings.xml')
    
    
    booleanParam(name: 'performDependencyCheck', defaultValue: false, description: 'Perform app dependency checks ?')
    booleanParam(name: 'performCodeQualityCheck', defaultValue: false, description: 'Perform app code Quality checks ?')
    booleanParam(name: 'performImageBuild', defaultValue: false, description: 'Flag to trigger app image build')
    booleanParam(name: 'performDevDeployment', defaultValue: false, description: 'Flag to trigger app deployment')
}  

environment {
    // APP_TYPE = "${params.appType}"
    // APP_NAME = "${params.appName}"
    // buildNumber = "${BUILD_NUMBER}"
    
    // GIT_URL = "${params.gitURL}" //"http://gogs.lab.app.10.16.202.119.nip.io"
    // GIT_BRANCH = "${params.gitBranch}"
    // GIT_APP_FOLDER = "${params.gitAppFolder}"

    // GIT_CRED = "gogscred"
    
    // // inject from properties file
    // SONAR_URL = "${sonarUrl}"

    // PERFORM_DEP_CHK = "${params.PerformDependencyCheck}"
    // PERFORM_CODE_CHK = "${params.PerformCodeQualityCheck}"
    // PERFORM_APP_DEPLOY = "${params.PerformDevDeployment}"

    // PROXY_SETTINGS="${mavenProxyFile}"

    APP_COORDINATE=""
  }

  stages {

    stage('init... ') {
        steps{
            script {
              modules.helper = load("${env.WORKSPACE}/jenkins/utility/helper.groovy")
              modules.helper.sayHello()

            //echo "${params.performDependencyCheck}"
              //Boolean.valueOf
            
          }
        }
    } //end stag

    stage('test'){
      steps{
        script{
          
          modules.helper.sayHello()
        }
      }
    }
    // stage('Stage - CI') {          
      
    //   steps {
    //     script {
    //       def task = build (
    //                   job: 'pipe-ci', 
    //                   parameters: [
    //                     string(name: 'appName', value: "${params.appName}"),
    //                     string(name: 'buildNumber', value: "${BUILD_NUMBER}"),
    //                     string(name: 'gitURL', value: "${params.gitURL}"),
    //                     string(name: 'gitBranch', value: "${params.gitBranch}"),
    //                     string(name: 'gitAppFolder', value: "${params.gitAppFolder}"),
    //                     string(name: 'sonarUrl', value: "${params.sonarUrl}"),
    //                     string(name: 'mavenProxyFile', value: "${params.mavenProxyFile}"),
    //                     booleanParam(name: 'performDependencyCheck', value: "${params.performDependencyCheck}"),
    //                     booleanParam(name: 'performCodeQualityCheck', value: "${params.performCodeQualityCheck}"),

    //                   ]

    //                 )

    //     }
    //   }
    // }  //end stage


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








