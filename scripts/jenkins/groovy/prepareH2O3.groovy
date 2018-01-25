import ai.h2o.ci.buildsummary.SimpleDetailsSummary
import ai.h2o.ci.buildsummary.StagesSummary

def call(final scmEnv, final String mode, final boolean ignoreChanges) {

  clearStageDirs()

  def pipelineContextFactory = load('h2o-3/scripts/jenkins/groovy/pipelineContext.groovy')
  def final pipelineContext = pipelineContextFactory('h2o-3', mode, scmEnv, ignoreChanges)

  buildSummary.get().addDetailsSummary(
          this,
          new SimpleDetailsSummary(this, env.GIT_SHA, env.BRANCH_NAME, env.COMMIT_MESSAGE, 'FIXME')
  )
  buildSummary.get().addStagesSummary(this, new StagesSummary())

  // FIXME
//  pipelineContext.getBuildSummary().addChangesSectionIfNecessary(this)

  // Archive scripts so we don't have to do additional checkouts when changing node
  pipelineContext.getUtils().stashScripts(this)

  // Load build script and execute it
  def buildH2O3 = load('h2o-3/scripts/jenkins/groovy/buildH2O3.groovy')
  buildH2O3(pipelineContext)
  return pipelineContext
}

void clearStageDirs() {
  def findCmd = "find . -maxdepth 1 -not -name 'h2o-3' -not -name h2o-3@tmp -not -name '.'"
  def deleteCmd = " -exec rm -rf '{}' ';'"
  def findDeleteCmd = findCmd + deleteCmd

  echo "About to delete these files/folders:"
  sh findCmd
  sh findDeleteCmd
}

return this
