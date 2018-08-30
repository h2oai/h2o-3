def call(customEnv, image, registry, buildConfig, timeoutValue, timeoutUnit, customArgs='', block) {

  def DEFAULT_DNS = '172.16.0.200'

  if (customArgs == null) {
    customArgs = ''
  }

  // by default, the image should be loaded
  def pullImage = true
  // First check that the image is present
  def imagePresent = sh(script: "docker inspect ${image} > /dev/null", returnStatus: true) == 0
  if (imagePresent) {
    echo "${image} present on host, checking versions..."
    // check that the image has expected SHA
    def expectedVersion = buildConfig.getExpectedImageVersion(image)
    if (expectedVersion == null) {
        echo "Cannot find expected version for ${image}. Pull."
        pullImage = true
    } else {
        def currentVersion = sh(script: "docker inspect --format=\'{{index .RepoDigests 0}}\' ${image}", returnStdout: true).trim()
        echo "current image version: ${currentVersion}"
        echo "expected image version: ${expectedVersion}"
        pullImage = currentVersion != expectedVersion
    }
  }

  if (pullImage) {
    echo "######### Pulling ${image} #########"
    withCredentials([usernamePassword(credentialsId: registry, usernameVariable: 'REGISTRY_USERNAME', passwordVariable: 'REGISTRY_PASSWORD')]) {
      sh "docker login -u $REGISTRY_USERNAME -p $REGISTRY_PASSWORD ${registry}"
      sh "docker pull ${image}"
    }
  } else {
    echo "######### Current version of ${image} already loaded #########"
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
