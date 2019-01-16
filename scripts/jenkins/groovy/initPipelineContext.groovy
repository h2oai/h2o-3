def call(final scmEnv, final String mode, final boolean ignoreChanges) {
  return call(scmEnv, mode, ignoreChanges, null)
}

def call(final scmEnv, final String mode, final boolean ignoreChanges, final List<String> gradleOpts) {

  clearStageDirs()

  def pipelineContextFactory = load('h2o-3/scripts/jenkins/groovy/pipelineContext.groovy')
  def final pipelineContext = pipelineContextFactory('h2o-3', mode, scmEnv, ignoreChanges, gradleOpts)

  pipelineContext.getBuildSummary().addDetailsSection(this, mode)
  pipelineContext.getBuildSummary().addChangesSectionIfNecessary(this)
  pipelineContext.getBuildSummary().addFailedTestsSection(this)

  // Archive scripts so we don't have to do additional checkouts when changing node
  pipelineContext.getUtils().stashScripts(this)

  return pipelineContext
}

void clearStageDirs() {
  sh "find `pwd` -maxdepth 1 -not -name 'h2o-3' -not -name h2o-3@tmp -not -path `pwd` -exec rm -rfv '{}' ';'"
}

return this
