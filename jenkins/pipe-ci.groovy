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
  - name: maven
    image: maven:3.3.9-jdk-8-alpine
    imagePullPolicy: IfNotPresent
    command:
    - cat
    tty: true
    volumeMounts:
      - name: maven-repo
        mountPath: /root/.m2
  - name: nodejs
    image: node:14.2-alpine
    imagePullPolicy: IfNotPresent
    command:
    - cat
    tty: true        
  volumes:
    - name: maven-repo
      persistentVolumeClaim:
        claimName: maven-storage
"""
    }
  }

parameters {
    string(name: 'appName', defaultValue: 'helloapp', description: 'Application Name')
    choice(name: 'appType', choices: ['Java', 'Nodejs'], description: 'Type of application - Java / Nodejs')
    choice(name: 'typeOfBuild', choices: ['Dockerfile', 'Buildpack'], description: 'Choose the app build type,  default -  Dockerfile')
    string(name: 'gitProject', defaultValue: 'gogsuser', description: 'git project for app')
    string(name: 'gitBranch', defaultValue: 'master', description: 'git project for app')
    booleanParam(name: 'PerformDependencyCheck', defaultValue: true, description: 'Perform app dependency checks ?')
    booleanParam(name: 'PerformCodeQualityCheck', defaultValue: true, description: 'Perform app code Quality checks ?')
    booleanParam(name: 'PerformImageBuild', defaultValue: false, description: 'Flag to trigger app image build')
    booleanParam(name: 'PerformDevDeployment', defaultValue: false, description: 'Flag to trigger app deployment')
}  

environment {
    APP_TYPE = "${params.appType}"
    APP_NAME = "${params.appName}"
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
    SONAR_URL = "http://sonarqube.lab.app.10.16.202.119.nip.io"
    PERFORM_DEP_CHK = "${params.PerformDependencyCheck}"
    PERFORM_CODE_CHK = "${params.PerformCodeQualityCheck}"
    PERFORM_APP_DEPLOY = "${params.PerformDevDeployment}"
  }

    stages {
        stage('Checkout SourceCode') {
            steps{
                script {
         	 	    checkout([$class: 'GitSCM', branches: [[name: "*/${GIT_BRANCH}"]],
			        doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: 
		 	        [[credentialsId: "${GIT_CRED}", url: "${GIT_URL}/${GIT_REPO}/${APP_NAME}.git"]]])
			          
		        }
            }
        }

        stage('Dependency Check') {
          when {
              environment name: "PERFORM_DEP_CHK", value: "true"
            }
            steps {
		        script {
			        echo "APP_TYPE: ${APP_TYPE}"
			        if (env.APP_TYPE == 'Java') {
                    container("maven"){
                      sh 'mvn clean verify org.owasp:dependency-check-maven:check -Ddependency-check-format=XML'
                      sh 'ls -la'
		                }
		                //dependencyCheck additionalArguments: '', odcInstallation: 'tkg-dep-chk', skipOnScmChange: true, skipOnUpstreamChange: true
		            } else if (env.APP_TYPE == 'Nodejs') {
		                echo 'Nodejs build is performed'
                        container("nodejs"){
                            //sh 'npm install'
                            dependencyCheck additionalArguments: ''' 
                                -o "./" 
                                -s "./"
                                -f "ALL" 
                                --prettyPrint''', odcInstallation: 'tkg-dep-chk'

                            dependencyCheckPublisher pattern: 'dependency-check-report.xml'
		                }
		            }
		        }
            }
        }	 //end stage

        stage('CodeQuality Check') {
          when {
              environment name: "PERFORM_CODE_CHK", value: "true"
            }
            steps {
              script {
                echo "APP_TYPE: ${APP_TYPE}"
                if (env.APP_TYPE == 'Java') {
                  // container("maven"){
                  //     //sh "mvn -s /tmp/nexus-mirror-cfg/settings-proxy.xml clean package sonar:sonar -Dsonar.host.url=${SONAR_URL}"  
                    //sh "mvn sonar:sonar -DskipTests -Dsonar.host.url=${SONAR_URL} -Dsonar.projectName=${APP_NAME} -Dsonar.projectVersion=${buildNumber} -Dsonar.projectKey=${APP_NAME}:app -Dsonar.sources=. -Dsonar.tests=. -Dsonar.test.inclusions=**/*Test*/** -Dsonar.exclusions=**/*Test*/** -Dsonar.java.binaries=target/classes -Dsonar.language=java"
                  // }
                    def scannerHome = tool 'tkg-sonar';
                    withSonarQubeEnv(credentialsId: 'sonar-auth-token') {
                        sh " ${scannerHome}/bin/sonar-scanner -Dsonar.host.url=${SONAR_URL} -Dsonar.projectName=${APP_NAME} -Dsonar.projectVersion=${buildNumber} -Dsonar.projectKey=${APP_NAME}:app -Dsonar.sources=. -Dsonar.tests=. -Dsonar.test.inclusions=**/*Test*/** -Dsonar.exclusions=**/*Test*/** -Dsonar.java.binaries=target/classes -Dsonar.language=java -X -Dsonar.dependencyCheck.securityHotspot=true -Dsonar.dependencyCheck.summarize=true -Dsonar.dependencyCheck.reportPath=${WORKSPACE}/target/dependency-check-report.xml -Dsonar.dependencyCheck.htmlReportPath=${WORKSPACE}/target/dependency-check-report.html -Dsonar.dependencyCheck.xmlReportPath=${WORKSPACE}/target/dependency-check-report.xml"
                    }
                      
                  } else if (env.APP_TYPE == 'Nodejs') {
                    container("nodejs"){
                        def scannerHome = tool 'tkg-sonar';
                        withSonarQubeEnv(credentialsId: 'sonar-auth-token') {
                            sh " ${scannerHome}/bin/sonar-scanner  -Dsonar.host.url=${SONAR_URL} -Dsonar.projectName=${APP_NAME} -Dsonar.projectVersion=1.0 -Dsonar.projectKey=${APP_NAME}:app -Dsonar.sources=. -Dsonar.language=js" 
                        }
                    }
                  }
              }
            }
        }	//end stage	


        stage('Build & Test App') {
            steps {
		        script {
			        echo "APP_TYPE: ${APP_TYPE}"
			        if (env.APP_TYPE == 'Java') {
		                container("maven"){
		                sh 'mvn clean package -DskipTests'
		                sh 'ls -la'
                    env.Dockerfile = readFile "${WORKSPACE}/Dockerfile" 
		            }
		            } else if (env.APP_TYPE == 'Nodejs') {
		                echo 'Nodejs build is performed'
                        container("nodejs"){
                            //sh 'npm install'
                            sh 'npm run test'
		                }
		            }
		        }
            }
        }	 //end stage

        stage("publish to nexus") {
                when { 
                    environment name: 'APP_TYPE', value: 'Java' 
                }
              steps {
				        echo 'STAGE: Publish Artefects to Nexus Repository' 
                echo "${env.Dockerfile}"
                script {
                  echo "APP_TYPE: ${APP_TYPE}"
                  if (env.APP_TYPE == 'Java') {
                      pom = readMavenPom file: "pom.xml";
                      filesByGlob = findFiles(glob: "target/*.${pom.packaging}");
                      echo "${filesByGlob[0].name} ${filesByGlob[0].path} ${filesByGlob[0].directory} ${filesByGlob[0].length} ${filesByGlob[0].lastModified}"
                      artifactPath = filesByGlob[0].path;
                      artifactExists = fileExists artifactPath;
                      if(artifactExists) {
                      echo "*** File: ${artifactPath}, group: ${pom.groupId}, packaging: ${pom.packaging}, version ${pom.version}";
                        
						            nexusArtifactUploader(
                            nexusVersion: NEXUS_VERSION,
                            protocol: NEXUS_PROTOCOL,
                            nexusUrl: NEXUS_URL,
                            groupId: "com.vmware",
                            version: "${buildNumber}",
                            repository: NEXUS_REPOSITORY,
                            credentialsId: NEXUS_CREDENTIAL_ID,
                            artifacts: [
                                // Artifact generated such as .jar, .ear and .war files.
                                [artifactId: pom.artifactId,
                                classifier: '',
                                file: artifactPath,
                                type: pom.packaging],
                                // Lets upload the pom.xml file for additional information for Transitive dependencies
                                [artifactId: pom.artifactId,
                                classifier: '',
                                file: "pom.xml",
                                type: "pom"]
                            ]
                          );
                      }
                    } else if (env.APP_TYPE == 'Nodejs') {
                        echo 'Nodejs build is performed'
                          
                      }
                }
            }
        }

        stage ('Starting ImageBuild job') {
          steps {
            script {
              if("${params.PerformImageBuild}" == "true") {
                if("${params.typeOfBuild}" == "Dockerfile"){
                    build job: 'pipe-cb-dockerfile', 
                    parameters: [
                      string(name: 'appImageName', value: "${APP_NAME}"), 
                      string(name: 'appImageTag', value: "${buildNumber}"), 
                      string(name: 'appType', value: "${APP_TYPE}"),
                      string(name: 'gitProject', value: "${GIT_REPO}"), 
                      string(name: 'gitBranch', value: "${GIT_BRANCH}"),
                      string(name: 'PerformDevDeployment', value: "${PERFORM_APP_DEPLOY}")
                    ]

                } else if("${params.typeOfBuild}" == "Buildpack"){
                    build job: 'pipe-cb-buildpack', 
                    parameters: [
                      string(name: 'appImageName', value: "${APP_NAME}"), 
                      string(name: 'appImageTag', value: "${buildNumber}"), 
                      string(name: 'appType', value: "${APP_TYPE}"),
                      string(name: 'gitProject', value: "${GIT_REPO}"), 
                      string(name: 'gitBranch', value: "${GIT_BRANCH}"),
                      string(name: 'PerformDevDeployment', value: "${PERFORM_APP_DEPLOY}")
                    ]
                }
              } 

            }
          }
        }


   }
}

