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
    '-Dsonar.projectName=${appName}',
    '-Dsonar.projectVersion=${buildNumber}',
    '-Dsonar.projectKey=${appName}:app',
    
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


def build(pomFolder, proxyPath,buildNumber){

  def cmd = mvncmd(pomFolder, proxyPath)
  def appVersion = sh (
    script : cmd + " -q -Dexec.executable=echo  -Dexec.args='\${project.version}' --non-recursive exec:exec", 
    returnStdout: true
  ).trim()

  appVersion = appVersion + "-" + buildNumber
  echo appVersion

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
    script : cmd + " -q -Dexec.executable=echo  -Dexec.args='\${project.groupId}:\${project.artifactId}:\${project.version}' --non-recursive exec:exec", 
    returnStdout: true
  ).trim()

}


def sayHello(){
  echo "hello from helper"
}


return this