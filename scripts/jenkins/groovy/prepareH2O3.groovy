def call(final scmEnv, final String mode, final boolean overrideDetectionChange) {

  def findCmd = "find . -maxdepth 1 -not -name 'h2o-3' -not -name h2o-3@tmp -not -name '.'"
  def deleteCmd = " -exec rm -rf '{}' ';'"
  def findDeleteCmd = findCmd + deleteCmd

  echo "About to delete these files/folders:"
  sh findCmd
  sh findDeleteCmd

  // get commit message
  def commitMessage = sh(script: 'cd h2o-3 && git log -1 --pretty=%B', returnStdout: true).trim()
  env.BRANCH_NAME = scmEnv['GIT_BRANCH'].replaceAll('origin/', '')
  env.GIT_SHA = scmEnv['GIT_COMMIT']
  env.GIT_DATE = "${sh(script: 'cd h2o-3 && git show -s --format=%ci', returnStdout: true).trim()}"

  // load buildConfig script and initialize the object
  def getChanges = load('h2o-3/scripts/jenkins/groovy/getChanges.groovy')
  def buildSummary = load('h2o-3/scripts/jenkins/groovy/buildSummary.groovy')
  def buildConfig = load('h2o-3/scripts/jenkins/groovy/buildConfig.groovy')
  buildConfig.initialize(this, mode, commitMessage, getChanges('h2o-3'), overrideDetectionChange, buildSummary)

  // Archive scripts so we don't have to do additional checkouts when changing node
  stash name: buildConfig.PIPELINE_SCRIPTS_STASH_NAME, includes: 'h2o-3/scripts/jenkins/groovy/*', allowEmpty: false

  // Load build script and execute it
  def buildH2O3 = load('h2o-3/scripts/jenkins/groovy/buildH2O3.groovy')
  buildH2O3(buildConfig)
  buildConfig.readVersion(readFile('h2o-3/h2o-3-DESCRIPTION'))
  echo "Build Config: ${buildConfig.toString()}"
  echo "Build Summary: ${buildConfig.getBuildSummary().toString()}"
  return buildConfig
}

return this
