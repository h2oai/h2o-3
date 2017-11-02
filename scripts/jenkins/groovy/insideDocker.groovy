def call(customEnv, buildConfig, timeoutValue, timeoutUnit, block) {

  def AWS_CREDENTIALS_ID = 'AWS S3 Credentials'

  def registry = 'docker.h2o.ai'
  def image = "${registry}/opsh2oai/h2o-3-runtime:${buildConfig.DOCKER_IMAGE_VERSION_TAG}"
  withCredentials([usernamePassword(credentialsId: registry, usernameVariable: 'REGISTRY_USERNAME', passwordVariable: 'REGISTRY_PASSWORD')]) {
      sh "docker login -u $REGISTRY_USERNAME -p $REGISTRY_PASSWORD ${registry}"
      sh "docker pull ${image}"
  }
  withEnv(customEnv) {
    timeout(time: timeoutValue, unit: timeoutUnit) {
      docker.withRegistry("https://${registry}") {
        withCredentials([file(credentialsId: 'c096a055-bb45-4dac-ba5e-10e6e470f37e', variable: 'JUNIT_CORE_SITE_PATH'), [$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: AWS_CREDENTIALS_ID, secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
          docker.image(image).inside("-e AWS_ACCESS_KEY_ID=\${AWS_ACCESS_KEY_ID} -e AWS_SECRET_ACCESS_KEY=\${AWS_SECRET_ACCESS_KEY} -v /home/0xdiag/smalldata:/home/0xdiag/smalldata -v /home/0xdiag/bigdata:/home/0xdiag/bigdata") {
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
