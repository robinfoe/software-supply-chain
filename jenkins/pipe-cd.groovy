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
  restartPolicy: Never
"""
    }
  }

parameters {
    string(name: 'appName', defaultValue: 'helloapp', description: 'Application Name')
    string(name: 'appImageVersion', defaultValue: '92', description: 'Application Image Tag/Version')
    string(name: 'appURL', defaultValue: 'helloappstg.lab.app.10.16.202.119.nip.io', description: 'App URL to map the ingress resource in cluster')
    string(name: 'gitProject', defaultValue: 'gogsuser', description: 'git project for app')
    string(name: 'gitBranch', defaultValue: 'master', description: 'git project for app')
    choice(name: 'chooseCluster', choices: ['staging', 'development', 'production'], description: 'Choose an environment to deploy app')
    //string(name: 'appEnvironment', defaultValue: 'dev', description: 'Which environment is the app being deployed to - dev , staging, prod ?')
    string(name: 'appNamespace', defaultValue: 'stg-env', description: 'Application Namespace in kubernetes')
   // string(name: 'builderImage', defaultValue: 'node:14.2-alpine', description: 'Docker image to use to build containerised app')
    string(name: 'ImageRepoName', defaultValue: 'devopsapps', description: 'App repo address in image registry')
    string(name: 'KubeResourceFolder', defaultValue: 'kubernetes', description: 'Kubernetes resource folder in the source code')

}  

environment {
    APP_NAME = "${params.appName}"
    buildNumber = "${BUILD_NUMBER}"
    GIT_URL = "http://gogs.lab.app.10.16.202.119.nip.io"
    GIT_CRED = "gogscred"
    GIT_REPO = "${params.gitProject}"
    GIT_BRANCH = "${params.gitBranch}"
    REGISTRY_URL = "core.harbor.lab.app.10.16.202.119.nip.io"
    REGISTRY_PROJECT = "${params.ImageRepoName}"
    REGISTRY_CRED = ""
    //APP_BUILDER_IMAGE = "${params.builderImage}"
    APP_ENV = "${params.appNamespace}"
    APP_DEPLOY_ENV = "${params.chooseCluster}" //"${params.appEnvironment}"
    CLUSTER_CHOICE = "${params.chooseCluster}"
    APP_IMAGE_NAME = "${params.appName}"
    IMAGE_VERSION = "${params.appImageVersion}"
  }
  options {
        timeout(time: 3, unit: 'MINUTES') 
    }

    stages {
        stage('Checkout k8s resources') {
            steps{
                script {
         	 	    checkout([$class: 'GitSCM', branches: [[name: "*/${GIT_BRANCH}"]],
			        doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: 
		 	        [[credentialsId: "${GIT_CRED}", url: "${GIT_URL}/${GIT_REPO}/${APP_NAME}.git"]]])
			          
		        }
            }
        }

        stage('Deploy to Kube-cluster'){
            steps{
                container('kubectl'){
                    script {

                        def kube_files = null
                        def k8s_deploy_env = "${APP_DEPLOY_ENV}"
                        
                        def sourceoutput = sh returnStdout: true, script: 'ls'
                        //sh "cat kubernetes/kustomization.yml"
                        if(sourceoutput.contains("${params.KubeResourceFolder}")){
                          echo "${params.KubeResourceFolder} folder is found in source"
                          //sh "ls -la ${params.KubeResourceFolder}"
                          echo "Image Tag => ${IMAGE_VERSION}"
                               //  b3.20200707.043933

                          def deployExists = fileExists "${params.KubeResourceFolder}/deployment.yaml"
                              if(deployExists){
                                def deployData = readYaml file: "${params.KubeResourceFolder}/deployment.yaml"
                                sh "rm ${params.KubeResourceFolder}/deployment.yaml"
                               if(IMAGE_VERSION.matches(/b(\d+).(\d+).(\d+)/)){
                                  deployData.spec.template.spec.containers[0].image = "docker.io/txconsole/${APP_NAME}:${IMAGE_VERSION}"
                               } else {
                                  deployData.spec.template.spec.containers[0].image = "${REGISTRY_URL}/${REGISTRY_PROJECT}/${APP_NAME}:${IMAGE_VERSION}"
                               }
                                writeYaml file: "${params.KubeResourceFolder}/deployment.yaml", data: deployData
                              }

                          def ingressExists = fileExists "${params.KubeResourceFolder}/ingress.yaml"
                              if(ingressExists){
                                def ingressData = readYaml file: "${params.KubeResourceFolder}/ingress.yaml"
                                sh "rm ${params.KubeResourceFolder}/ingress.yaml"
                                ingressData.spec.rules[0].host = "${params.appURL}"
                                writeYaml file: "${params.KubeResourceFolder}/ingress.yaml", data: ingressData
                                //sh "cat ${params.KubeResourceFolder}/ingress.yaml"
                              }
                          def httpProxyExists = fileExists "${params.KubeResourceFolder}/HTTPProxy.yaml"
                              if(httpProxyExists){
                                def httpProxyData = readYaml file: "${params.KubeResourceFolder}/HTTPProxy.yaml"
                                sh "rm ${params.KubeResourceFolder}/HTTPProxy.yaml"
                                httpProxyData.spec.virtualhost.fqdn = "${params.appURL}"
                                writeYaml file: "${params.KubeResourceFolder}/HTTPProxy.yaml", data: httpProxyData
                                //sh "cat ${params.KubeResourceFolder}/HTTPProxy.yaml"
                              }  

                          def kuberesources = sh returnStdout: true, script: "ls ${params.KubeResourceFolder}"
                            if(kuberesources.contains("kustomization")){
                              echo "'kustomization.yml' is found in source"
                              sh "kubectl kustomize ${params.KubeResourceFolder} > kube_files"
                              sh 'cat kube_files'

                            } else if(kuberesources.contains("base")){

                              deployExists = fileExists "${params.KubeResourceFolder}/base/deployment.yaml"
                                if(deployExists){
                                  def deployData = readYaml file: "${params.KubeResourceFolder}/base/deployment.yaml"
                                  sh "rm ${params.KubeResourceFolder}/base/deployment.yaml"
                                if(IMAGE_VERSION.matches(/b(\d+).(\d+).(\d+)/)){
                                    deployData.spec.template.spec.containers[0].image = "docker.io/txconsole/${APP_NAME}:${IMAGE_VERSION}"
                                } else {
                                    deployData.spec.template.spec.containers[0].image = "${REGISTRY_URL}/${REGISTRY_PROJECT}/${APP_NAME}:${IMAGE_VERSION}"
                                }
                                  writeYaml file: "${params.KubeResourceFolder}/base/deployment.yaml", data: deployData
                                }

                              ingressExists = fileExists "${params.KubeResourceFolder}/base/ingress.yaml"
                              if(ingressExists){
                                def ingressData = readYaml file: "${params.KubeResourceFolder}/base/ingress.yaml"
                                sh "rm ${params.KubeResourceFolder}/base/ingress.yaml"
                                ingressData.spec.rules[0].host = "${params.appURL}"
                                writeYaml file: "${params.KubeResourceFolder}/base/ingress.yaml", data: ingressData
                                //sh "cat ${params.KubeResourceFolder}/ingress.yaml"
                              }

                              httpProxyExists = fileExists "${params.KubeResourceFolder}/base/HTTPProxy.yaml"
                              if(httpProxyExists){
                                def httpProxyData = readYaml file: "${params.KubeResourceFolder}/base/HTTPProxy.yaml"
                                sh "rm ${params.KubeResourceFolder}/base/HTTPProxy.yaml"
                                httpProxyData.spec.virtualhost.fqdn = "${params.appURL}"
                                writeYaml file: "${params.KubeResourceFolder}/base/HTTPProxy.yaml", data: httpProxyData
                                //sh "cat ${params.KubeResourceFolder}/HTTPProxy.yaml"
                              }

                              if(k8s_deploy_env == "development"){

                                echo "'development' Env is found in config"
                                sh "kubectl kustomize ${params.KubeResourceFolder}/base > kube_files"
                                sh 'cat kube_files'

                              } else {
                                  echo "${k8s_deploy_env} Env is found in config"
                                  sh "kubectl kustomize ${params.KubeResourceFolder}/overlays/${k8s_deploy_env} > kube_files"
                                  sh 'cat kube_files'
                              }

                            }
                            // updated ingress
                            

                        } else {
                          echo "'kubernetes' folder is NOT found in source"
                        }

                        // def currentCluster  = [ "knv-wd","knv-wd-admin@knv-wd","https://10.16.202.43:6443" ]
                        // def dev_cluster     = [ "jazz-dev-k8s","jazz-dev-k8s-admin@jazz-dev-k8s","https://10.50.23.225:6443" ]
                        // def nonprod_cluster = [ "jazz-nonprod-k8s","jazz-nonprod-k8s-admin@jazz-nonprod-k8s","https://10.50.23.229:6443" ]
                        // def mgmt_cluster    = [ "jazz-mgmt-k8s","jazz-mgmt-k8s-admin@jazz-mgmt-k8s","https://10.50.23.232:6443" ]

                        def currentCluster     = [ "knv-wd","knv-wd-admin@knv-wd","https://10.16.202.43:6443" ]
                        if("${CLUSTER_CHOICE}" == "development"){
                          currentCluster = currentCluster
                        } else if("${CLUSTER_CHOICE}" == "staging"){
                          currentCluster = currentCluster
                        } else if("${CLUSTER_CHOICE}" == "production"){
                          currentCluster = currentCluster
                        } 

                        
                        echo "CLUSTER CHOICE: ${CLUSTER_CHOICE}, CLUSTER_NAME: ${currentCluster[0]}, CLUSTER_CTX: ${currentCluster[1]}, CLUSTER_URL: ${currentCluster[2]}"
                        echo "ENV CHOICE: ${APP_ENV}"

                        withKubeConfig(caCertificate: '', clusterName: "${currentCluster[0]}", contextName: "${currentCluster[1]}", 
                            credentialsId: 'tkg-sg-config', namespace: '', serverUrl: "${currentCluster[2]}") {

                            // check/creating for existence of app-environment in the kube-cluster
                            try{
                                sh "kubectl get ns ${APP_ENV}"
                            } catch(err){
                               echo err.getMessage()
                               echo "Creating app-namespace in the cluster: ${APP_ENV}"
                               sh "kubectl create ns ${APP_ENV}"
                               sleep time: 500, unit: 'MILLISECONDS'
                            }
                            // build registry secret in the defined cluster and environment
                            try{
                                sh "kubectl delete secret regcred -n ${APP_ENV}"
                            } catch(err){
                               echo err.getMessage()
                            }
                            sleep time: 500, unit: 'MILLISECONDS'
                            try{
                              withCredentials([usernamePassword(credentialsId: 'regcred', passwordVariable: 'passwd', usernameVariable: 'uname')]) {
                                sh "kubectl -n ${APP_ENV} create secret docker-registry regcred --docker-username=${uname} --docker-password=${passwd} --docker-server=${REGISTRY_URL} --insecure-skip-tls-verify=true"
                              }
                            } catch(err){
                               echo err.getMessage()
                            }
                            sleep time: 500, unit: 'MILLISECONDS'

                              def exists = fileExists 'kube_files'
                              if(exists){
                                sh "kubectl apply -f kube_files -n ${APP_ENV}"
                              } else {
                                sh "kubectl apply -f ${params.KubeResourceFolder} -n ${APP_ENV}"
                              }
                              // try{
                              //     sh "kubectl rollout pause deployment ${APP_NAME} -n ${APP_ENV}"
                              // } catch(err){
                              //   echo err.getMessage()
                              // }
                             //pattern = "\'{\"spec\":{\"rules\":[{\"host\":\"${params.appURL}\"}]}}\'"
                             //pattern = "\'[{\"op\": \"replace\", \"path\": \"/spec/rules/0/host\", \"value\":\"${params.appURL}\"}]\'"
                             
                            //  '[{"op": "replace", "path": "/spec/containers/0/image", "value":"newimage"}]'
                            //  //echo "${pattern}"
                            //  try{
                            //    //  b3.20200707.043933
                            //    sh "kubectl label deployment ${APP_NAME} --overwrite=true currentImage=${APP_IMAGE_NAME}-${IMAGE_VERSION} -n ${APP_ENV}"
                            //    if(IMAGE_VERSION.matches(/b(\d+).(\d+).(\d+)/)){
                            //       sh "kubectl set image deployment ${APP_NAME} ${APP_NAME}=docker.io/txconsole/${APP_NAME}:${IMAGE_VERSION} --insecure-skip-tls-verify=true -n ${APP_ENV}"
                            //    } else {
                            //       sh "kubectl set image deployment ${APP_NAME} ${APP_NAME}=${REGISTRY_URL}/${REGISTRY_PROJECT}/${APP_NAME}:${IMAGE_VERSION} --insecure-skip-tls-verify=true -n ${APP_ENV}"
                            //    }
                                
                            //     //sh "kubectl patch ing ${APP_NAME} -p ${pattern} -n ${APP_ENV} "
                            //     //sh "kubectl patch ing ${APP_NAME} --type='json' -p=${pattern} -n ${APP_ENV}"
                            //     sh "kubectl rollout resume deployment ${APP_NAME} -n ${APP_ENV}"
                            // } catch(err){
                            //    echo err.getMessage()
                            //    sh "kubectl rollout resume deployment ${APP_NAME} -n ${APP_ENV}"
                            // }
                             sleep time: 1000, unit: 'MILLISECONDS'
                    
                            appReadySts = sh ( script: "kubectl rollout status deployment ${APP_NAME} -n ${APP_ENV}", encoding: 'UTF-8', returnStdout: true)
                            if(appReadySts.contains("successfully")){
                                echo "The app ${APP_NAME} is deployed successfully."
                            }
                            sh "kubectl get all,ing,HTTPProxy -n ${APP_ENV} --show-labels"
                            //sh "kubectl get ing -n ${APP_ENV} --show-labels"
                        }

                        
                    }
                } //container ends
            }
        } //stage ends
        
    }

      
   
}

