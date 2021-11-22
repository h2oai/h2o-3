def call(final pipelineContext) {

    // Load required scripts
    def insideDocker = load('h2o-3/scripts/jenkins/groovy/insideDocker.groovy')
    def makeTarget = load('h2o-3/scripts/jenkins/groovy/makeTarget.groovy')
    def config = pipelineContext.getBuildConfig()

    def stageName = 'Build H2O-3 Public'
    def buildClosure = {
        pipelineContext.getBuildSummary().addStageSummary(this, stageName, 'h2o-3')
        pipelineContext.getBuildSummary().setStageDetails(this, stageName, env.NODE_NAME, env.WORKSPACE)
        try {
            // Launch docker container, build h2o-3
            def buildEnv = config.getBuildEnv()
                + "PYTHON_VERSION=${config.VERSIONS.PYTHON.active}"
                + "R_VERSION=${config.VERSIONS.R.latest_3}"
                + "JAVA_VERSION=${config.VERSIONS.JAVA.first}"
            def timeoutMinutes = 100
            stage(stageName) {
                pipelineContext.getUtils().stashXGBoostWheels(this, pipelineContext)
                insideDocker(buildEnv, config.getDefaultImage(), config.DOCKER_REGISTRY, config, timeoutMinutes, 'MINUTES') {
                    try {
                        makeTarget(pipelineContext) {
                            target = 'test-build-h2o-public'
                            hasJUnit = false
                            archiveFiles = false
                            makefilePath = config.MAKEFILE_PATH
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
