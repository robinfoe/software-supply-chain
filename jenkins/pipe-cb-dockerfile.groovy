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
    string(name: 'imageTag', defaultValue: '01', description: 'Container Image Tag')
    
    string(name: 'buildNumber', defaultValue: '5', description: 'Build Number, propagated from parent task')

    // GIT
    string(name: 'gitURL', defaultValue: 'https://github.com/robinfoe/bookstore-ms.git', description: 'Git Clone URL')
    string(name: 'gitBranch', defaultValue: 'master', description: 'git project for app')
    string(name: 'gitAppFolder', defaultValue: 'book', description: 'Application Root Folder, leave blank pom.xml is in Root directory')

    string(name: 'mavenProxyFile', defaultValue: '/tmp/m2/tmp-nexus-proxy.xml', description: 'Location to settings.xml')
    string(name: 'appCoordinate', defaultValue: 'com.vmware.sample.istio:book:1.0-SNAPSHOT-67', description: 'Location to settings.xml')
}  

environment {
    APP_NAME = "${params.appImageName}"
    buildNumber = "${params.buildNumber}"


    GIT_URL = "${params.gitURL}" 
    GIT_BRANCH = "${params.gitBranch}"
    GIT_APP_FOLDER = "${params.gitAppFolder}"

    GIT_CRED = "gogscred"

}

  stages {
    
    stage('Grab Dockerfile from Git') {
        steps{
            script {
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
            pullMavenArtifact( "${params.gitAppFolder}" , "${params.mavenProxyFile}" , "${params.appCoordinate}")
          }
        }
      }
    }  //end stage
        

  }

}


def pullMavenArtifact(pomFolder, proxyPath, appCoordinate){

  //sh mvncmd(pomFolder, proxyPath) + ' org.apache.maven.plugins:maven-dependency-plugin:3.1.2:get -Dartifact=' + appCoordinate + ' -DoutputDirectory=${WORKSPACE}/'+pomFolder+'/target'

  sh 'mkdir -p '+pomFolder+'/target'
  sh mvncmd(pomFolder, proxyPath) + ' dependency:get -Ddest=./'+pomFolder+'/target -Dartifact='+appCoordinate
  // sh mvncmd(pomFolder, proxyPath) + ' org.apache.maven.plugins:maven-dependency-plugin:3.1.2:get -Dartifact=' + appCoordinate + ' -Ddest=${WORKSPACE}/'+pomFolder+'/target'


  // sh 'pwd'
  // // sh 'whoami'
  // sh 'ls -a'
  // sh 'ls -a '+pomFolder+'/'
  // sh 'ls -a '+pomFolder+'/target'

  // sh mvncmd(pomFolder, proxyPath) + ' clean package '
  // sh mvncmd(pomFolder, proxyPath) + ' clean package '
  // sh mvncmd(pomFolder, proxyPath) + ' clean package '
  // sh mvncmd(pomFolder, proxyPath) + ' clean package '
  // sh mvncmd(pomFolder, proxyPath) + ' clean package '
  // sh 'mv *.jar '+pomFolder+'/target/'
  
  // sh 'sleep 5m'

}


def mvncmd(pomFolder, proxyPath){

  // if(pomFolder?.trim()){
  //    return "mvn -f ./"+pomFolder+"/pom.xml -s "+proxyPath+ " "
  // }
  return "mvn -s "+proxyPath+" "
}



