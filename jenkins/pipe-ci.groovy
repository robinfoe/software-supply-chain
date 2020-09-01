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

    string(name: 'mavenProxyFile', defaultValue: '/tmp/m2/ivy-settings.xml', description: 'Location to settings.xml')
    
    booleanParam(name: 'performDependencyCheck', defaultValue: false, description: 'Perform app dependency checks ?')
    booleanParam(name: 'performCodeQualityCheck', defaultValue: false, description: 'Perform app code Quality checks ?')
}  

environment {

    PERFORM_DEP_CHK = "${params.performDependencyCheck}"
    PERFORM_CODE_CHK = "${params.performCodeQualityCheck}"
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
                          //  credentialsId: "${GIT_CRED}", 
                            url: "${params.gitURL}"
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
            modules.helper.runDependencyScan( params.gitAppFolder , params.mavenProxyFile )
          }
        }
      }
    }  //end stage


    stage('CodeQuality Check') {
      when {environment name: "PERFORM_CODE_CHK", value: "true"}

      steps {
        script {
            container("maven"){
              modules.helper.runCodeQualityCheck( params.gitAppFolder , params.mavenProxyFile , 
                                                    params.appName, params.buildNumber )
            }
        }
      }
    } //end stage 


    stage('Build ,  Test and Publish') {
      steps {
        script {
          container("maven"){

            modules.helper.build( params.gitAppFolder , params.mavenProxyFile, params.buildNumber )
            modules.helper.publish( params.gitAppFolder , params.mavenProxyFile)

            // set this for return value
            env.APP_COORDINATE=modules.helper.getJarCoordinate( params.gitAppFolder , params.mavenProxyFile)
          }
        }
      } 
    } //end stage   
  }
}






