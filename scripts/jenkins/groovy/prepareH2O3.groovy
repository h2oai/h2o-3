def call(final String mode, final String nodeLabel) {

  // Archive scripts so we don't have to do additional checkouts when changing node
  archiveArtifacts artifacts: "h2o-3/scripts/jenkins/groovy/*", allowEmptyArchive: false

  // Load build script and execute it
  def buildH2O3 = load('h2o-3/scripts/jenkins/groovy/buildH2O3.groovy')
  buildH2O3()

  def buildConfig = load('h2o-3/scripts/jenkins/groovy/buildConfig.groovy')
  def commitMessage = sh(script: 'cd h2o-3 && git log -1 --pretty=%B', returnStdout: true).trim()
  buildConfig.initialize(mode, nodeLabel, commitMessage)
  return buildConfig
}

return this
