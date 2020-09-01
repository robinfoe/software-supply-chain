// Shorthand functions

def runDependencyScan(pomFolder, proxyPath){
  
  sh mvncmd(pomFolder, proxyPath) + ' clean verify org.owasp:dependency-check-maven:check -Ddependency-check-format=XML'
                      
}


def mvncmd(pomFolder, proxyPath){

  if(pomFolder?.trim()){
     return "mvn -f ./"+pomFolder+"/pom.xml -s "+proxyPath+ " "
  }
  return "mvn -s "+proxyPath+" "
}


def runCodeQualityCheck(pomFolder, proxyPath,  appName, buildNumber){

  def sonarParams = [
    '-Dsonar.projectName='+appName,
    '-Dsonar.projectVersion='+buildNumber,
    '-Dsonar.projectKey='+appName+':app',
    
    '-Dsonar.scm.disabled=true',
    '-Dsonar.sources=src/main',
    '-Dsonar.tests=src/test',

    // scan behaviour
    '-Dsonar.dependencyCheck.securityHotspot=true',
    '-Dsonar.dependencyCheck.summarize=true',
    '-Dsonar.dependencyCheck.htmlReportPath=target/dependency-check-report.html',
    
    // what to include 
    '-Dsonar.test.inclusions=**/*Test*/**' ,
    '-Dsonar.exclusions=**/*Test*/**' ,
    
    // language
    '-Dsonar.java.binaries=target/classes' ,
    '-Dsonar.language=java'
  ]

 sh mvncmd(pomFolder, proxyPath) + ' package sonar:sonar ' + sonarParams.join(' ')

}


def containerizeAndPush(gitAppFolder, imagePrefix, appName,  buildNumber){

  sh 'ls -l'
  sh 'pwd'

  def imageLoc=imagePrefix+'/'+appName+':'+buildNumber
  def kanikoParams = [

    '-f `pwd`/'+gitAppFolder+'/Dockerfile',
    '-c `pwd`/'+gitAppFolder,

    '--insecure',  
    '--insecure-registry',
    '--skip-tls-verify',
    '--insecure-pull',
    '--skip-tls-verify-pull',
    '--cache=false',
    
    '--destination="' + imageLoc  + '"',
    
    '--verbosity=info'
  ]

  sh '/kaniko/executor ' + kanikoParams.join(' ')
}



def build(pomFolder, proxyPath,buildNumber){

  def cmd = mvncmd(pomFolder, proxyPath)
  def appVersion = sh (
    script : cmd + " -q -Dexec.executable=echo  -Dexec.args='\${project.version}' --non-recursive exec:exec", 
    returnStdout: true
  ).trim()

  appVersion = appVersion + "-" + buildNumber

  // execute version change before perform buil 
  sh cmd + " versions:set -DnewVersion=" + appVersion

  // initiate build.... 
  sh cmd + ' clean package -DskipTests'
  return appVersion
  
}

def publish( pomFolder, proxyPath ){

  def cmd = mvncmd(pomFolder, proxyPath)
  sh cmd + ' deploy -DskipTests'

}


def getJarCoordinate(pomFolder, proxyPath){

  def cmd = mvncmd(pomFolder, proxyPath)
  def appVersion = sh (
    script : cmd + " -q -Dexec.executable=echo  -Dexec.args='''\${project.groupId}:\${project.artifactId}:\${project.version}''' --non-recursive exec:exec", 
    returnStdout: true
  ).trim()

  return appVersion
}

def pullMavenArtifact(pomFolder, proxyPath, appCoordinate){
  sh 'mkdir -p '+pomFolder+'/target'
  sh mvncmd('', proxyPath) + ' dependency:get -Ddest=./'+pomFolder+'/target -Dartifact='+appCoordinate
}



def retagImage(fromImage , toImage){
  // call within kaniko container 
  def kanikoParams = [

    '--dockerfile /dev/stdin',
    '--insecure',  
    '--insecure-registry',
    '--skip-tls-verify',
    '--insecure-pull',
    '--skip-tls-verify-pull',
    '--cache=false',
    
    '--destination="' + toImage  + '"',
    
    '--verbosity=info'
  ]

  sh 'echo "FROM '+fromImage+'" | /kaniko/executor ' + kanikoParams.join(' ')

}



def generateKubeResource(appFolder, kubeFolder, imagePrefix, appName, buildNumber,appUrlSuffix, deployEnvironment ){
  // call within kaniko container 
  def imageURL = generateImageTag(imagePrefix, appName, buildNumber, deployEnvironment)
  def appURL = generateAppURL(appName, appUrlSuffix, deployEnvironment)

  sh 'ls -l '+appFolder+'/'+kubeFolder
  sh 'whoami'
  //sh 'ls -l ${appFolder}/${kubeFolder}'

  def sourceoutput = sh returnStdout: true, script: 'ls '+appFolder

  if(sourceoutput.contains( kubeFolder )){

    def yalmConstruct = readYaml file: "${appFolder}/${kubeFolder}/deployment.yaml"
    sh "rm ${appFolder}/${kubeFolder}/deployment.yaml"
    yalmConstruct.spec.template.spec.containers[0].image = imageURL
    writeYaml file: "${appFolder}/${kubeFolder}/deployment.yaml", data: yalmConstruct



    yalmConstruct = readYaml file: "${appFolder}/${kubeFolder}/ingress.yaml"
    sh "rm ${appFolder}/${kubeFolder}/ingress.yaml"
    yalmConstruct.spec.rules[0].host = appURL
    writeYaml file: "${appFolder}/${kubeFolder}/ingress.yaml", data: yalmConstruct

  }
}
 
def deployKubeResource(kubeConstructFolder, namespace ,appName){

  def sourceoutput = sh returnStdout: true, script: 'ls '+kubeConstructFolder
  
  def files = sourceoutput.split('\n')
  for(file in files){
  
    try{
        sh 'kubectl delete --wait=true --timeout=3600s -n ' + namespace + ' -f ' +kubeConstructFolder + '/'+file
    }catch(Exception e){/** IGNORED **/}
    sh 'kubectl create -n ' + namespace + ' -f ' +kubeConstructFolder + '/' + file

  }

  // sh 'kubectl wait -n ' + namespace + ' --for=condition=Ready pods --selector app='+appName+' --timeout=1200 ' 
}


def isSIT(deployEnvironment){
  return "sit".equals(deployEnvironment)
}

def generateNamespace(namespacePrefix, deployEnvironment){

   if ( isSIT(deployEnvironment) ) {
    return namespacePrefix + "-sit"  
  }else{
    return namespacePrefix + "-prod"  
  }

}

def generateAppURL(appName, appUrlSuffix, deployEnvironment){

  if ( isSIT(deployEnvironment) ) {
    return appName + ".sit" + appUrlSuffix 
  }else{
    return appName + ".prod" + appUrlSuffix 
  }
}

def generateImageTag(imagePrefix, appName, buildNumber, deployEnvironment){

  if ( isSIT(deployEnvironment) ) {
    return imagePrefix + "/" + appName + ":" + buildNumber
  }else{
    return imagePrefix + "/" + appName + ":prod-" + buildNumber
  }

}



def sayHello(){
  echo "hello from helper"
}


return this