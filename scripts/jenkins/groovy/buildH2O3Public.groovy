def call(final pipelineContext) {

    final String PYTHON_VERSION = '3.7'
    final String R_VERSION = '3.4.1'
    final String JAVA_VERSION = '8'

    // Load required scripts
    def insideDocker = load('h2o-3/scripts/jenkins/groovy/insideDocker.groovy')
    def makeTarget = load('h2o-3/scripts/jenkins/groovy/makeTarget.groovy')

    def stageName = 'Build H2O-3 Public'
    def buildClosure = {
        pipelineContext.getBuildSummary().addStageSummary(this, stageName, 'h2o-3')
        pipelineContext.getBuildSummary().setStageDetails(this, stageName, env.NODE_NAME, env.WORKSPACE)
        try {
            // Launch docker container, build h2o-3
            def buildEnv = pipelineContext.getBuildConfig().getBuildEnv() + "PYTHON_VERSION=${PYTHON_VERSION}" + "R_VERSION=${R_VERSION}" + "JAVA_VERSION=${JAVA_VERSION}"
            def timeoutMinutes = 100
            stage(stageName) {
                pipelineContext.getUtils().stashXGBoostWheels(this, pipelineContext)
                insideDocker(buildEnv, pipelineContext.getBuildConfig().getDefaultImage(), pipelineContext.getBuildConfig().DOCKER_REGISTRY, pipelineContext.getBuildConfig(), timeoutMinutes, 'MINUTES') {
                    try {
                        makeTarget(pipelineContext) {
                            target = 'test-build-h2o-public'
                            hasJUnit = false
                            archiveFiles = false
                            makefilePath = pipelineContext.getBuildConfig().MAKEFILE_PATH
                            activatePythonEnv = true
                        }
                    } finally {
                        archiveArtifacts "**/*.log, **/*_log, **/out.*, **/*py.out.txt, **/java*out.txt, **/status.*"
                    }
                }
            }
            pipelineContext.getBuildSummary().markStageSuccessful(this, stageName)
        } catch (Exception e) {
            pipelineContext.getBuildSummary().markStageFailed(this, stageName)
            throw e
        }
    }
    buildClosure()
}

return this
