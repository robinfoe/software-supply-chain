def modules = [:]
pipeline {
  agent { //maven:3.3.9-jdk-8-alpine
    kubernetes {      
            yaml """
kind: Pod
apiVersion: v1
metadata:
  name: ci-pod
spec:
  containers:
  - name: jnlp
    image: jenkins/jnlp-slave:3.27-1
    args: ['\$(JENKINS_SECRET)', '\$(JENKINS_NAME)']
  - name: maven
    image: maven:3.6.3-adoptopenjdk-11
    imagePullPolicy: IfNotPresent
    command:
    - cat
    tty: true
    volumeMounts:
    - name: mvn-proxy
      mountPath: /tmp/m2   
  volumes:
  - name: mvn-proxy
    configMap:
      name: nexus-mirror-cfg
"""
    }
  }

parameters {

    // APP
    string(name: 'appName', defaultValue: 'book', description: 'Application Name')
    string(name: 'buildNumber', defaultValue: '0', description: 'Build Number, propagated from parent task')

    // GIT
    string(name: 'gitURL', defaultValue: 'https://github.com/robinfoe/bookstore-ms.git', description: 'Git Clone URL')
    string(name: 'gitBranch', defaultValue: 'master', description: 'git project for app')
    string(name: 'gitAppFolder', defaultValue: '', description: 'Application Root Folder, leave blank pom.xml is in Root directory')

    string(name: 'sonarUrl', defaultValue: 'http://sonarqube.pipeline.tanzu-no.de', description: 'Sonarqube URL')
    string(name: 'mavenProxyFile', defaultValue: '/tmp/m2/ivy-settings.xml', description: 'Location to settings.xml')
    
    booleanParam(name: 'performDependencyCheck', defaultValue: false, description: 'Perform app dependency checks ?')
    booleanParam(name: 'performCodeQualityCheck', defaultValue: false, description: 'Perform app code Quality checks ?')
}  

environment {

    APP_NAME = "${params.appName}"
    buildNumber = "${params.buildNumber}"
    
    GIT_URL = "${params.gitURL}" 
    GIT_BRANCH = "${params.gitBranch}"
    GIT_APP_FOLDER = "${params.gitAppFolder}"

    GIT_CRED = "gogscred"
    
    // inject from properties file
    SONAR_URL = "${sonarUrl}"

    PERFORM_DEP_CHK = "${params.performDependencyCheck}"
    PERFORM_CODE_CHK = "${params.performCodeQualityCheck}"
    PROXY_SETTINGS="${mavenProxyFile}"
  }

  stages {

    stage('Checkout SourceCode') {
        steps{
            script {
              modules.helper = load("${env.WORKSPACE}/jenkins/utility/helper.groovy")
              
              checkout(
                  [
                    $class: 'GitSCM', 
                    branches: [[name: "*/${GIT_BRANCH}"]],

                    doGenerateSubmoduleConfigurations: false, extensions: [], 
                      submoduleCfg: [], 
                      userRemoteConfigs: [
                          [
                          //  credentialsId: "${GIT_CRED}", 
                            url: "${GIT_URL}"
                          ]
                      ]
                  ]
              )
            
          }
        }
    } //end stag

    stage('Dependency Check') {          
      when { environment name: "PERFORM_DEP_CHK", value: "true" }

      steps {
        script {
          container("maven"){
            modules.helper.runDependencyScan( GIT_APP_FOLDER , PROXY_SETTINGS)
          }
        }
      }
    }  //end stage


    stage('CodeQuality Check') {
      when {environment name: "PERFORM_CODE_CHK", value: "true"}

      steps {
        script {
            container("maven"){
              modules.helper.runCodeQualityCheck( GIT_APP_FOLDER , PROXY_SETTINGS , APP_NAME, buildNumber )
            }
        }
      }
    } //end stage 


    stage('Build ,  Test and Publish') {
      steps {
        script {
          container("maven"){

            modules.helper.build(GIT_APP_FOLDER , PROXY_SETTINGS, buildNumber )
            modules.helper.publish(GIT_APP_FOLDER , PROXY_SETTINGS)

            // set this for return value
            env.APP_COORDINATE=modules.helper.getJarCoordinate(GIT_APP_FOLDER , PROXY_SETTINGS)
          }
        }
      } 
    } //end stage   
  }
}






