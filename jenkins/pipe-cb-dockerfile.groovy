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
  - name: kaniko
    image: gcr.io/kaniko-project/executor:debug-v0.23.0
    imagePullPolicy: IfNotPresent
    command:  
    - /busybox/cat
    tty: true
    volumeMounts:
      - name: registry-cred
        mountPath: /kaniko/.docker
  volumes:
    - name: registry-cred
      projected:
        sources:
        - secret:
            name: registry-cred
            items:
                - key: .dockerconfigjson
                  path: config.json
"""
    }
  }

parameters {
    string(name: 'appImageName', defaultValue: 'helloapp', description: 'Application Image Name')
    string(name: 'appImageTag', defaultValue: '96', description: 'Application Image Tag')
    string(name: 'gitProject', defaultValue: 'gogsuser', description: 'git project for app')
    string(name: 'gitBranch', defaultValue: 'master', description: 'git project for app')
    choice(name: 'appType', choices: ['Java', 'Nodejs'], description: 'Type of application - Java / Nodejs')
    string(name: 'ImageRepoName', defaultValue: 'devopsapps', description: 'App repo address in image registry')
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
    IMAGE_TAG = "${params.appImageTag}"
    APP_GROUP_ID = "vmware"
    APP_BINARY_TYPE = "jar"
    APP_DOMAIN = "lab.app.10.16.202.119.nip.io"

  }

    stages {
        stage('Containerised App') {
          steps{
            script {
                container("kaniko"){
                  echo "Create app image"
                  echo "App Type => ${params.appType}"

                  checkout([$class: 'GitSCM', branches: [[name: "*/${GIT_BRANCH}"]],
                  doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: 
                  [[credentialsId: "${GIT_CRED}", url: "${GIT_URL}/${GIT_REPO}/${APP_NAME}.git"]]])

                  if("${params.appType}" == "Java"){
                        checkout changelog: false, poll: false, 
                      scm: [$class: 'GitSCM', branches: [[name: "*/${GIT_BRANCH}"]], 
                      doGenerateSubmoduleConfigurations: false, 
                      extensions: [[$class: 'SparseCheckoutPaths', sparseCheckoutPaths: [[path: 'Dockerfile']]]], submoduleCfg: [], 
                      userRemoteConfigs: [[credentialsId: "${GIT_CRED}",  url: "${GIT_URL}/${GIT_REPO}/${APP_NAME}.git"]]]

                      sh "wget ${NEXUS_PROTOCOL}://${NEXUS_URL}/repository/${NEXUS_REPOSITORY}/com/${APP_GROUP_ID}/${APP_NAME}/${IMAGE_TAG}/${APP_NAME}-${IMAGE_TAG}.${APP_BINARY_TYPE}"
                      sh 'ls -la'
                      sh "mkdir target && cp *.${APP_BINARY_TYPE} target"
                  }
                  sh 'ls -la'
                  //sh 'env'
                  sh '/kaniko/executor -f `pwd`/Dockerfile -c `pwd` --insecure --insecure-registry --skip-tls-verify --insecure-pull --skip-tls-verify-pull --cache=false --destination="${REGISTRY_URL}/${REGISTRY_PROJECT}/${APP_NAME}:${IMAGE_TAG}" --verbosity=info'
              }
            }
          }
        }

        stage ('Starting appDeploy job') {
          steps {
            script {
              if("${params.PerformDevDeployment}" == "true") {

                  build job: 'pipe-cd', 
                  parameters: [
                    string(name: 'appName', value: "${APP_NAME}"), 
                    string(name: 'appImageVersion', value: "${IMAGE_TAG}"), 
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

