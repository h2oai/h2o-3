def call(buildConfig) {

  def PYTHON_VERSION = '3.5'
  def R_VERSION = '3.4.1'

  // Load required scripts
  def insideDocker = load('h2o-3/scripts/jenkins/groovy/insideDocker.groovy')
  def buildTarget = load('h2o-3/scripts/jenkins/groovy/buildTarget.groovy')
  def customEnv = load('h2o-3/scripts/jenkins/groovy/customEnv.groovy')

  def stageName = 'Build H2O-3'
  
  withCustomCommitStates(scm, buildConfig.H2O_OPS_TOKEN, "${buildConfig.getGitHubCommitStateContext(stageName)}") {
    buildConfig.addStageSummary(this, stageName)
    buildConfig.setStageNode(this, stageName, env.NODE_NAME)
    try {
      // Launch docker container, build h2o-3, create test packages and archive artifacts
      def buildEnv = customEnv(buildConfig) + "PYTHON_VERSION=${PYTHON_VERSION}" + "R_VERSION=${R_VERSION}"
      insideDocker(buildEnv, buildConfig.DEFAULT_IMAGE, buildConfig.DOCKER_REGISTRY, 30, 'MINUTES') {
        stage(stageName) {
          try {
            buildTarget {
              target = 'build-h2o-3'
              hasJUnit = false
              archiveFiles = false
              makefilePath = 'docker/Makefile.jenkins'
            }
            buildTarget {
              target = 'test-package-py'
              hasJUnit = false
              archiveFiles = false
              makefilePath = 'docker/Makefile.jenkins'
            }
            buildTarget {
              target = 'test-package-r'
              hasJUnit = false
              archiveFiles = false
              makefilePath = 'docker/Makefile.jenkins'
            }
            if (buildConfig.getBuildHadoop()) {
              buildTarget {
                target = 'test-package-hadoop'
                hasJUnit = false
                archiveFiles = false
                makefilePath = 'docker/Makefile.jenkins'
              }
            }
            if (buildConfig.langChanged(buildConfig.LANG_JS)) {
              buildTarget {
                target = 'test-package-js'
                hasJUnit = false
                archiveFiles = false
                makefilePath = 'docker/Makefile.jenkins'
              }
            }
            if (buildConfig.langChanged(buildConfig.LANG_JAVA)) {
              buildTarget {
                target = 'test-package-java'
                hasJUnit = false
                archiveFiles = false
                makefilePath = 'docker/Makefile.jenkins'
              }
            }
          } finally {
            archiveArtifacts """
              h2o-3/docker/Makefile.jenkins,
              h2o-3/h2o-py/dist/*.whl,
              h2o-3/build/h2o.jar,
              h2o-3/h2o-3/src/contrib/h2o_*.tar.gz,
              h2o-3/h2o-assemblies/genmodel/build/libs/genmodel.jar,
              h2o-3/test-package-*.zip,
              **/*.log, **/out.*, **/*py.out.txt, **/java*out.txt, **/tests.txt, **/status.*
            """
          }
        }
      }
      buildConfig.markStageSuccessful(this, stageName)
      echo "************************${buildConfig.getBuildSummary()}***************************"
    } catch (Exception e) {
      buildConfig.markStageFailed(this, stageName)
      throw e
    }
  }
}

return this
