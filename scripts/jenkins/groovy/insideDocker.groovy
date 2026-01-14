def call(customEnv, image, registry, buildConfig, timeoutValue, timeoutUnit, customArgs='',addToDockerGroup = false, awsCredsPrefix = '', block) {

  if (customArgs == null) {
    customArgs = ''
  }
  if (awsCredsPrefix == null) {
    awsCredsPrefix = ''
  }

  retryWithDelay(3 /* retries */, 120 /* delay in sec */) {
    withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: "H2O-AWS_CT-JENKINS-H2O-ECR"]]) {
      H2O_ECR_PWD = sh(script: 'aws ecr get-login-password --region us-east-1', returnStdout: true).trim()
      sh """
            docker login --username AWS --password $H2O_ECR_PWD ${registry}
            docker pull ${image}
        """
    }
  }

  withEnv(customEnv) {
    timeout(time: timeoutValue, unit: timeoutUnit) {
      docker.withRegistry("https://${registry}") {
        withCredentials([/**file(credentialsId: 'c096a055-bb45-4dac-ba5e-10e6e470f37e', variable: 'JUNIT_CORE_SITE_PATH'),**/ [$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: "${awsCredsPrefix}AWS_ACCESS_KEY_ID", credentialsId: 'H2O-AWS_CT-JENKINS-SHARED-SERVICE-PROD', secretKeyVariable: "${awsCredsPrefix}AWS_SECRET_ACCESS_KEY"]]) {
          withCredentials([string(credentialsId: 'DRIVERLESS_AI_LICENSE_KEY', variable: 'DRIVERLESS_AI_LICENSE_KEY')/**, string(credentialsId: "H2O3_GET_PROJECT_TOKEN", variable:  "H2O3_GET_PROJECT_TOKEN")**/]) {
            dockerGroupIdAdd = ""
            if (addToDockerGroup) {
              dockerGroupName = "docker"
              dockerGroupId = sh(script: "awk -F: '/$dockerGroupName/ {print \$3}' /etc/group", returnStdout: true).trim()
              if (dockerGroupId == "") {
                error "cannot find gid of $dockerGroupName"
              } else {
                dockerGroupIdAdd = "--group-add ${dockerGroupId}"
              }
            }
//            // TODO add -e H2O3_GET_PROJECT_TOKEN=${H2O3_GET_PROJECT_TOKEN}
           docker.image(image).inside("--user=root:root --entrypoint='' --init ${dockerGroupIdAdd} -e AWS_CREDS_PREFIX='${awsCredsPrefix}' -e ${awsCredsPrefix}AWS_ACCESS_KEY_ID=${awsCredsPrefix}\${AWS_ACCESS_KEY_ID} -e ${awsCredsPrefix}AWS_SECRET_ACCESS_KEY=${awsCredsPrefix}\${AWS_SECRET_ACCESS_KEY} -e DRIVERLESS_AI_LICENSE_KEY=${DRIVERLESS_AI_LICENSE_KEY} -v /home/jenkins:/home/jenkins/repos  -v /mnt/h2o-shared-data:/mnt/h2o-shared-data ${customArgs}") {
              sh """
              id
              printenv | sort
            """
              block()
            }
          }
        }
      }
    }
  }
}

return this
