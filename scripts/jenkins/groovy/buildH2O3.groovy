def call() {

  // Load required scripts
  def insideDocker = load('h2o-3/scripts/jenkins/groovy/insideDocker.groovy')
  def buildTarget = load('h2o-3/scripts/jenkins/groovy/buildTarget.groovy')
  def customEnv = load('h2o-3/scripts/jenkins/groovy/customEnv.groovy')

  // Launch docker container, build h2o-3, create test packages and archive artifacts
  def buildEnv = customEnv() + "PYTHON_VERSION=${env.pythonVersion}" + "R_VERSION=${env.rVersion}"
  insideDocker(buildEnv, 15, 'MINUTES') {
    stage ('Build H2O-3') {
      try {
        buildTarget {
          target = 'build-h2o-3'
          hasJUnit = false
          archiveFiles = false
        }
        buildTarget {
          target = 'test-package-py'
          hasJUnit = false
          archiveFiles = false
        }
        buildTarget {
          target = 'test-package-r'
          hasJUnit = false
          archiveFiles = false
        }
        buildTarget {
          target = 'test-package-js'
          hasJUnit = false
          archiveFiles = false
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
}

return this
