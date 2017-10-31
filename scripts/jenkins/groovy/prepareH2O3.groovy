def call(final String mode, final String nodeLabel, final boolean overrideDetectionChange) {

  def findCmd = "find . -maxdepth 1 -not -name 'h2o-3' -not -name h2o-3@tmp -not -name '.'"
  def deleteCmd = " -exec rm -rf '{}' ';'"
  def findDeleteCmd = findCmd + deleteCmd

  echo "About to delete these files/folders:"
  sh findCmd
  sh findDeleteCmd

  // Archive scripts so we don't have to do additional checkouts when changing node
  archiveArtifacts artifacts: "h2o-3/scripts/jenkins/groovy/*", allowEmptyArchive: false

  // get commit message
  def commitMessage = sh(script: 'cd h2o-3 && git log -1 --pretty=%B', returnStdout: true).trim()

  // get changes between merge base and this commit
  sh 'cd h2o-3 && git fetch --no-tags --progress https://github.com/h2oai/h2o-3 +refs/heads/master:refs/remotes/origin/master'
  def mergeBaseSHA = sh(script: "cd h2o-3 && git merge-base HEAD origin/master", returnStdout: true).trim()
  def changes = sh(script: "cd h2o-3 && git diff --name-only ${mergeBaseSHA}", returnStdout: true).trim().tokenize('\n')

  // load buildConfig script and initialize the object
  def buildConfig = load('h2o-3/scripts/jenkins/groovy/buildConfig.groovy')
  buildConfig.initialize(mode, nodeLabel, commitMessage, changes, overrideDetectionChange)
  echo "Build Config: ${buildConfig.toString()}"

  // Load build script and execute it
  def buildH2O3 = load('h2o-3/scripts/jenkins/groovy/buildH2O3.groovy')
  buildH2O3(buildConfig)
  return buildConfig
}

return this
