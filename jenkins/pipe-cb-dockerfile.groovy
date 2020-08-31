def modules = [:]
pipeline {
  agent {
    kubernetes {      
            yaml """
kind: Pod
apiVersion: v1
metadata:
  name: cb-pod
spec:
  containers:
  - name: jnlp
    image: jenkins/jnlp-slave:3.27-1
    args: ['\$(JENKINS_SECRET)', '\$(JENKINS_NAME)']
    securityContext:
      fsGroup: 10000
  - name: maven
    image: maven:3.6.3-adoptopenjdk-11
    imagePullPolicy: IfNotPresent
    command:
    - cat
    tty: true
    securityContext:
      fsGroup: 10000
    volumeMounts:
    - name: mvn-proxy
      mountPath: /tmp/m2   

  - name: kaniko
    image: gcr.io/kaniko-project/executor:debug-v0.23.0
    imagePullPolicy: IfNotPresent
    command:  
    - /busybox/cat
    tty: true
    volumeMounts:
      - name: docker-config
        mountPath: /kaniko/.docker

  volumes:
  - name: mvn-proxy
    configMap:
      name: nexus-mirror-cfg
  - name: docker-config
    configMap:
      name: docker-config 
"""
    }
  }

parameters {


      // APP
    string(name: 'appName', defaultValue: 'book', description: 'Application Name')
    // string(name: 'imageTag', defaultValue: '01', description: 'Container Image Tag')
    string(name: 'imagePrefix', defaultValue: 'docker.io/robinfoe', description: 'Image prefix ( Default point to dockerhub)')
    
    string(name: 'buildNumber', defaultValue: '67', description: 'Build Number, propagated from parent task')

    // GIT
    string(name: 'gitURL', defaultValue: 'https://github.com/robinfoe/bookstore-ms.git', description: 'Git Clone URL')
    string(name: 'gitBranch', defaultValue: 'master', description: 'git project for app')
    string(name: 'gitAppFolder', defaultValue: 'book', description: 'Application Root Folder, leave blank pom.xml is in Root directory')

    string(name: 'mavenProxyFile', defaultValue: '/tmp/m2/ivy-settings.xml', description: 'Location to settings.xml')
    string(name: 'appCoordinate', defaultValue: 'com.vmware.sample.istio:book:1.0-SNAPSHOT-67', description: 'Location to settings.xml')
}  

environment {
    APP_NAME = "${params.appImageName}"
   // buildNumber = "${params.buildNumber}"


    GIT_URL = "${params.gitURL}" 
    GIT_BRANCH = "${params.gitBranch}"
    GIT_APP_FOLDER = "${params.gitAppFolder}"

    GIT_CRED = "gogscred"

}

  stages {
    
    stage('Grab Dockerfile from Git') {
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
                            url: "${GIT_URL}"
                          ]
                      ]
                  ]
              )
            
          }
        }
    } //end stag


    stage('Grab jar from nexus') {          
        
        steps {
          script {
            container("maven"){
              modules.helper.pullMavenArtifact( "${params.gitAppFolder}" , "${params.mavenProxyFile}" , "${params.appCoordinate}")
          }
        }
      }
    }  //end stage


    stage('Build and Publish Container') {            
      steps {
        script {
          container("kaniko"){

            echo params.gitAppFolder

            sh 'cd ' + params.gitAppFolder
            modules.helper.containerizeAndPush( "${params.imagePrefix}" , "${params.appName}" , "${params.buildNumber}" )

          }
        }
     }
    }  //end stage
        

  }

}





