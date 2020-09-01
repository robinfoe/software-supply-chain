
def modules = [:]
def jobVar = [:]
def proceedToProduction = false


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
    // choice(name: 'appType', choices: ['Java', 'Nodejs'], description: 'Type of application - Java / Nodejs')
    string(name: 'appUrlSuffix', defaultValue: '.app.pipeline.ivy.tanzu-no.de', description: 'URL Suffix')
    string(name: 'namespacePrefix', defaultValue: 'tanzu-app-pipeline', description: 'Namespace prefix for application')
    // choice(name: 'typeOfBuild', choices: ['Dockerfile', 'Buildpack'], description: 'Choose the app build type,  default -  Dockerfile')

    //IMAGE_PREFIX
    string(name: 'imagePrefix', defaultValue: 'docker.io/robinfoe', description: 'Image prefix ( Default point to dockerhub)')

    // GIT
    string(name: 'gitURL', defaultValue: 'http://gitea.pipeline.tanzu-no.de/ivy/software-supply/bookstore-ms.git', description: 'Git Clone URL')
    string(name: 'gitBranch', defaultValue: 'ivy-1.0', description: 'git project for app')
    string(name: 'gitAppFolder', defaultValue: 'book', description: 'Application Root Folder, leave blank pom.xml is in Root directory')
    string(name: 'kubeResourceFolder', defaultValue: 'kubernetes', description: 'Kubernetes resource folder in the source code')

    // string(name: 'sonarUrl', defaultValue: 'http://sonarqube.pipeline.tanzu-no.de', description: 'Sonarqube URL')
    string(name: 'mavenProxyFile', defaultValue: '/tmp/m2/ivy-settings.xml', description: 'Location to settings.xml')
    
    
    booleanParam(name: 'performDependencyCheck', defaultValue: false, description: 'Perform app dependency checks ?')
    booleanParam(name: 'performCodeQualityCheck', defaultValue: false, description: 'Perform app code Quality checks ?')
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

          // echo jobVar.appCoordinate
          def task = build (
                      job: 'pipe-cb-dockerfile', 
                      parameters: [
                        string(name: 'appName', value: "${params.appName}"),
                        string(name: 'imagePrefix', value: "${params.imagePrefix}"),
                        
                        string(name: 'buildNumber', value: "${BUILD_NUMBER}"),
                        
                        string(name: 'gitURL', value: "${params.gitURL}"),
                        string(name: 'gitBranch', value: "${params.gitBranch}"),
                        string(name: 'gitAppFolder', value: "${params.gitAppFolder}"),
                        
                        string(name: 'mavenProxyFile', value: "${params.mavenProxyFile}"),
                        string(name: 'appCoordinate', value: jobVar.appCoordinate ),
                        
                  
                      ]
                    )

        }
      }
    }  //end stage


    stage('Stage - CD - SIT ') {
      steps {
        script {

          def task = build (
                      job: 'pipe-cd', 
                      parameters: [
                        string(name: 'appName', value: params.appName),
                        string(name: 'imagePrefix', value: params.imagePrefix),

                        string(name: 'appUrlSuffix', value: params.appUrlSuffix),
                        string(name: 'namespacePrefix', value: params.namespacePrefix),
                        string(name: 'deployEnvironment', value: "sit"),
                        string(name: 'kubeResourceFolder', value: params.kubeResourceFolder),
                        
                        string(name: 'buildNumber', value: "${BUILD_NUMBER}"),
                        // string(name: 'buildNumber', value: "14"),
                        
                        string(name: 'gitURL', value: params.gitURL ),
                        string(name: 'gitBranch', value: params.gitBranch ),
                        string(name: 'gitAppFolder', value: params.gitAppFolder )
                      ]
                    )




          try{

              timeout(time: 30, unit: 'MINUTES') { 
                proceedToProduction = input(
                    id: 'Proceed1', message: 'Deploy to Production ?', parameters: [
                    [$class: 'BooleanParameterDefinition', defaultValue: true, description: '', name: 'Please confirm you sure to proceed']
                  ])
              }
            }catch(Exception e){
              proceedToProduction = false
            }
        }
      }
    } //end stage 


    stage('Stage - CD - Prod ') {
      when { expression { proceedToProduction  } } 
      steps {
        script {
          def task = build (
                      job: 'pipe-cd', 
                      parameters: [
                        string(name: 'appName', value: params.appName),
                        string(name: 'imagePrefix', value: params.imagePrefix),

                        string(name: 'appUrlSuffix', value: params.appUrlSuffix),
                        string(name: 'namespacePrefix', value: params.namespacePrefix),
                        string(name: 'deployEnvironment', value: "prod"),
                        string(name: 'kubeResourceFolder', value: params.kubeResourceFolder),
                        
                        string(name: 'buildNumber', value: "${BUILD_NUMBER}"),
                        // string(name: 'buildNumber', value: "14"),
                        
                        string(name: 'gitURL', value: params.gitURL ),
                        string(name: 'gitBranch', value: params.gitBranch ),
                        string(name: 'gitAppFolder', value: params.gitAppFolder )
                      ]
                    )

        }
      }
    } //end stage 



  }
}








