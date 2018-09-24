def call(customEnv, image, registry, buildConfig, timeoutValue, timeoutUnit, customArgs='', block) {

  def DEFAULT_DNS = '172.16.0.200'

  if (customArgs == null) {
    customArgs = ''
  }

  retryWithDelay(3 /* retries */, 120 /* delay in sec */) {
    withCredentials([usernamePassword(credentialsId: registry, usernameVariable: 'REGISTRY_USERNAME', passwordVariable: 'REGISTRY_PASSWORD')]) {
      sh "docker login -u $REGISTRY_USERNAME -p $REGISTRY_PASSWORD ${registry}"
      sh "docker pull ${image}"
    }
  }

  withEnv(customEnv) {
    timeout(time: timeoutValue, unit: timeoutUnit) {
      docker.withRegistry("https://${registry}") {
        withCredentials([file(credentialsId: 'c096a055-bb45-4dac-ba5e-10e6e470f37e', variable: 'JUNIT_CORE_SITE_PATH'), [$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'AWS S3 Credentials', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
          docker.image(image).inside("--init --dns ${DEFAULT_DNS} -e AWS_ACCESS_KEY_ID=\${AWS_ACCESS_KEY_ID} -e AWS_SECRET_ACCESS_KEY=\${AWS_SECRET_ACCESS_KEY} -v /home/0xdiag:/home/0xdiag ${customArgs}") {
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
