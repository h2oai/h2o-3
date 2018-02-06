def call(customEnv, image, registry, timeoutValue, timeoutUnit, customArgs='', block) {

  def AWS_CREDENTIALS_ID = 'AWS S3 Credentials'

  if (customArgs == null) {
    customArgs = ''
  }

  withCredentials([usernamePassword(credentialsId: registry, usernameVariable: 'REGISTRY_USERNAME', passwordVariable: 'REGISTRY_PASSWORD')]) {
// FIXME
//      sh "docker login -u $REGISTRY_USERNAME -p $REGISTRY_PASSWORD ${registry}"
//      sh "docker pull ${image}"
  }

  withEnv(customEnv) {
    timeout(time: timeoutValue, unit: timeoutUnit) {
      docker.withRegistry("https://${registry}") {
        withCredentials([file(credentialsId: 'c096a055-bb45-4dac-ba5e-10e6e470f37e', variable: 'JUNIT_CORE_SITE_PATH'), [$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: AWS_CREDENTIALS_ID, secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
          String masterHostName = sh (script: "echo ${env.JENKINS_URL} | sed -e 's/^http:\\/\\///' -e 's/:[0-9]*\\///g'", returnStdout: true).trim()
          String masterIP = sh(script: "dig +short ${masterHostName}", returnStdout: true).trim()
          docker.image(image).inside("--init --add-host=mr-0xc1:${masterIP} -e AWS_ACCESS_KEY_ID=\${AWS_ACCESS_KEY_ID} -e AWS_SECRET_ACCESS_KEY=\${AWS_SECRET_ACCESS_KEY} -v /home/0xdiag/smalldata:/home/0xdiag/smalldata -v /home/0xdiag/bigdata:/home/0xdiag/bigdata ${customArgs}") {
            sh 'id'
            sh 'printenv'
            block()
          }
        }
      }
    }
  }
}

return this
