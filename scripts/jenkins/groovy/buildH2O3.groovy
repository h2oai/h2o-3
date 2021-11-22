def call(final pipelineContext) {

    // Load required scripts
    def insideDocker = load('h2o-3/scripts/jenkins/groovy/insideDocker.groovy')
    def makeTarget = load('h2o-3/scripts/jenkins/groovy/makeTarget.groovy')
    def config = pipelineContext.getBuildConfig()

    def stageName = 'Build H2O-3'
    def buildClosure = {
        pipelineContext.getBuildSummary().addStageSummary(this, stageName, 'h2o-3')
        pipelineContext.getBuildSummary().setStageDetails(this, stageName, env.NODE_NAME, env.WORKSPACE)
        try {
            // Launch docker container, build h2o-3, create test packages and archive artifacts
            def buildEnv = config.getBuildEnv() + 
                    "PYTHON_VERSION=${config.VERSIONS.PYTHON.active}" + 
                    "R_VERSION=${config.VERSIONS.R.latest_3}" + 
                    "JAVA_VERSION=${config.VERSIONS.JAVA.first}"
            def timeoutMinutes = config.getBuildHadoop() ? 50 : 15
            stage(stageName) {
                pipelineContext.getUtils().stashXGBoostWheels(this, pipelineContext)
                insideDocker(buildEnv, config.getDefaultImage(), config.DOCKER_REGISTRY, config, timeoutMinutes, 'MINUTES') {
                    try {
                        makeTarget(pipelineContext) {
                            target = 'build-h2o-3'
                            hasJUnit = false
                            archiveFiles = false
                            makefilePath = config.MAKEFILE_PATH
                            activatePythonEnv = true
                        }
                        makeTarget(pipelineContext) {
                            target = 'test-package-py'
                            hasJUnit = false
                            archiveFiles = false
                            makefilePath = config.MAKEFILE_PATH
                            activatePythonEnv = true
                        }
                        makeTarget(pipelineContext) {
                            target = 'test-package-r'
                            hasJUnit = false
                            archiveFiles = false
                            makefilePath = config.MAKEFILE_PATH
                            activatePythonEnv = true
                        }
                        if (config.getBuildHadoop()) {
                            makeTarget(pipelineContext) {
                                target = 'test-package-hadoop'
                                hasJUnit = false
                                archiveFiles = false
                                makefilePath = config.MAKEFILE_PATH
                                activatePythonEnv = true
                            }
                        }
                        if (config.componentChanged(config.COMPONENT_JS)) {
                            makeTarget(pipelineContext) {
                                target = 'test-package-js'
                                hasJUnit = false
                                archiveFiles = false
                                makefilePath = config.MAKEFILE_PATH
                                activatePythonEnv = true
                            }
                        }
                        if (config.componentChanged(config.COMPONENT_JAVA)) {
                            makeTarget(pipelineContext) {
                                target = 'test-package-java'
                                hasJUnit = false
                                archiveFiles = false
                                makefilePath = config.MAKEFILE_PATH
                                activatePythonEnv = true
                            }
                            makeTarget(pipelineContext) {
                                target = 'test-package-minimal'
                                hasJUnit = false
                                archiveFiles = false
                                makefilePath = pipelineContext.getBuildConfig().MAKEFILE_PATH
                                activatePythonEnv = true
                            }
                        } else {
                            makeTarget(pipelineContext) {
                                target = 'test-package-gradle'
                                hasJUnit = false
                                archiveFiles = false
                                makefilePath = config.MAKEFILE_PATH
                                activatePythonEnv = true
                            }
                        }
                    } finally {
                        archiveArtifacts "**/*.log, **/out.*, **/*py.out.txt, **/java*out.txt, **/status.*"
                        config.TEST_PACKAGES_COMPONENTS.each { component ->
                            if (config.stashComponent(component) || 
                                    (component == config.COMPONENT_JAVA && config.stashComponent(config.COMPONENT_ANY))) {
                                echo "********* Stash ${component} *********"
                                pipelineContext.getUtils().stashFiles(
                                        this,
                                        config.getStashNameForTestPackage(component),
                                        "h2o-3/test-package-${component}.zip",
                                        true
                                )
                            }
                        }
                        pipelineContext.getUtils().stashFiles(
                                this,
                                config.H2O_JAR_STASH_NAME,
                                "h2o-3/build/h2o.jar"
                        )
                    }
                }
            }
            pipelineContext.getBuildSummary().markStageSuccessful(this, stageName)
        } catch (Exception e) {
            pipelineContext.getBuildSummary().markStageFailed(this, stageName)
            throw e
        }
    }
    if (env.BUILDING_FORK) {
        withCustomCommitStates(scm, config.H2O_OPS_TOKEN, stageName) {
            buildClosure()
        }
    } else {
        buildClosure()
    }
}

return this
