def call() {

  // Archive scripts so we don't have to do additional checkouts when changing node
  archiveArtifacts artifacts: "h2o-3/scripts/jenkins/groovy/*", allowEmptyArchive: false

  // Load build script and execute it
  def buildH2O3 = load('h2o-3/scripts/jenkins/groovy/buildH2O3.groovy')
  buildH2O3()
}

return this
