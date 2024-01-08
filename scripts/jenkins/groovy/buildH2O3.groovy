def call(final pipelineContext) {

    final String PYTHON_VERSION = '3.7'
    final String R_VERSION = '3.4.1'
    final String JAVA_VERSION = '8'

    // Load required scripts
    def insideDocker = load('h2o-3/scripts/jenkins/groovy/insideDocker.groovy')
    def makeTarget = load('h2o-3/scripts/jenkins/groovy/makeTarget.groovy')

    def stageName = 'Build H2O-3'
    def buildClosure = {
        pipelineContext.getBuildSummary().addStageSummary(this, stageName, 'h2o-3')
        pipelineContext.getBuildSummary().setStageDetails(this, stageName, env.NODE_NAME, env.WORKSPACE)
        try {
            // Launch docker container, build h2o-3, create test packages and archive artifacts
            def buildEnv = pipelineContext.getBuildConfig().getBuildEnv() + "PYTHON_VERSION=${PYTHON_VERSION}" + "R_VERSION=${R_VERSION}" + "JAVA_VERSION=${JAVA_VERSION}"
            def timeoutMinutes = pipelineContext.getBuildConfig().getBuildHadoop() ? 50 : 15
            stage(stageName) {
                pipelineContext.getUtils().stashXGBoostWheels(this, pipelineContext)
                insideDocker(buildEnv, pipelineContext.getBuildConfig().getDefaultImage(), pipelineContext.getBuildConfig().DOCKER_REGISTRY, pipelineContext.getBuildConfig(), timeoutMinutes, 'MINUTES') {
                    try {
                        makeTarget(pipelineContext) {
                            target = 'build-h2o-3'
                            hasJUnit = false
                            archiveFiles = false
                            makefilePath = pipelineContext.getBuildConfig().MAKEFILE_PATH
                            activatePythonEnv = true
                        }
                        makeTarget(pipelineContext) {
                            target = 'test-package-py'
                            hasJUnit = false
                            archiveFiles = false
                            makefilePath = pipelineContext.getBuildConfig().MAKEFILE_PATH
                            activatePythonEnv = true
                        }
                        makeTarget(pipelineContext) {
                            target = 'test-package-r'
                            hasJUnit = false
                            archiveFiles = false
                            makefilePath = pipelineContext.getBuildConfig().MAKEFILE_PATH
                            activatePythonEnv = true
                        }
                        if (pipelineContext.getBuildConfig().getBuildHadoop()) {
                            makeTarget(pipelineContext) {
                                target = 'test-package-hadoop'
                                hasJUnit = false
                                archiveFiles = false
                                makefilePath = pipelineContext.getBuildConfig().MAKEFILE_PATH
                                activatePythonEnv = true
                            }
                        }
                        if (pipelineContext.getBuildConfig().componentChanged(pipelineContext.getBuildConfig().COMPONENT_JS)) {
                            makeTarget(pipelineContext) {
                                target = 'test-package-js'
                                hasJUnit = false
                                archiveFiles = false
                                makefilePath = pipelineContext.getBuildConfig().MAKEFILE_PATH
                                activatePythonEnv = true
                            }
                        }
                        if (pipelineContext.getBuildConfig().componentChanged(pipelineContext.getBuildConfig().COMPONENT_JAVA)) {
                            makeTarget(pipelineContext) {
                                target = 'test-package-java'
                                hasJUnit = false
                                archiveFiles = false
                                makefilePath = pipelineContext.getBuildConfig().MAKEFILE_PATH
                                activatePythonEnv = true
                            }
                            makeTarget(pipelineContext) {
                                target = 'test-package-main'
                                hasJUnit = false
                                archiveFiles = false
                                makefilePath = pipelineContext.getBuildConfig().MAKEFILE_PATH
                                activatePythonEnv = true
                            }
                            makeTarget(pipelineContext) {
                                target = 'test-package-minimal'
                                hasJUnit = false
                                archiveFiles = false
                                makefilePath = pipelineContext.getBuildConfig().MAKEFILE_PATH
                                activatePythonEnv = true
                            }
                            makeTarget(pipelineContext) {
                                target = 'test-package-steam'
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
                                makefilePath = pipelineContext.getBuildConfig().MAKEFILE_PATH
                                activatePythonEnv = true
                            }
                        }
                    } finally {
                        archiveArtifacts "**/*.log, **/out.*, **/*py.out.txt, **/java*out.txt, **/status.*"
                        pipelineContext.getBuildConfig().TEST_PACKAGES_COMPONENTS.each { component ->
                            if (pipelineContext.getBuildConfig().stashComponent(component) || 
                                    (component == pipelineContext.getBuildConfig().COMPONENT_JAVA && pipelineContext.getBuildConfig().stashComponent(pipelineContext.getBuildConfig().COMPONENT_ANY))) {
                                echo "********* Stash ${component} *********"
                                pipelineContext.getUtils().stashFiles(
                                        this,
                                        pipelineContext.getBuildConfig().getStashNameForTestPackage(component),
                                        "h2o-3/test-package-${component}.zip",
                                        true
                                )
                            }
                        }
                        pipelineContext.getUtils().stashFiles(
                                this,
                                pipelineContext.getBuildConfig().H2O_JAR_STASH_NAME,
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
        withCustomCommitStates(scm, pipelineContext.getBuildConfig().H2O_OPS_TOKEN, stageName) {
            buildClosure()
        }
    } else {
        buildClosure()
    }
}

return this
