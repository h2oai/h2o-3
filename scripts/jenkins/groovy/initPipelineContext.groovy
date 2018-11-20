import ai.h2o.ci.buildsummary.DetailsSummary

def call(final scmEnv, final String mode, final boolean ignoreChanges) {
    return call(scmEnv, mode, ignoreChanges, null)
}

def call(final scmEnv, final String mode, final boolean ignoreChanges, final List<String> gradleOpts) {

    clearStageDirs()

    def pipelineContextFactory = load('h2o-3/scripts/jenkins/groovy/pipelineContext.groovy')
    def final pipelineContext = pipelineContextFactory('h2o-3', mode, scmEnv, ignoreChanges, gradleOpts)

    DetailsSummary detailsSummary = new DetailsSummary()
    buildSummary.get().addDetailsSummary(this, detailsSummary)

    if (mode) {
        detailsSummary.setEntry(this, 'Mode', mode)
    }
    detailsSummary.setEntry(this, 'Commit Message', env.COMMIT_MESSAGE)
    detailsSummary.setEntry(this, 'Branch', env.BRANCH_NAME)
    detailsSummary.setEntry(this, 'Git SHA', env.GIT_SHA)

    if (env.BUILDING_FORK) {
        detailsSummary.setEntry(this, 'Building Fork', "true")
    }


    if (mode == 'MODE_SINGLE_TEST') {
        detailsSummary.setEntry(this, 'Python', params.singleTestPyVersion)
        detailsSummary.setEntry(this, 'R', params.singleTestRVersion)
        detailsSummary.setEntry(this, 'Test', "${params.testComponent} - ${context.params.testPath}")
        detailsSummary.setEntry(this, 'java.xmx', params.singleTestXmx)
        detailsSummary.setEntry(this, '# H2O Nodes', params.singleTestNumNodes)
        detailsSummary.setEntry(this, '# Runs:</strong', params.singleTestNumRuns)
    }

    pipelineContext.getBuildSummary().addChangesSectionIfNecessary(this)
    pipelineContext.getBuildSummary().addFailedTestsSection(this)

    // Archive scripts so we don't have to do additional checkouts when changing node
    pipelineContext.getUtils().stashScripts(this)

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
