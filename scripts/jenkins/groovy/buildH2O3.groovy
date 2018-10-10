def call(final pipelineContext) {

    final String PYTHON_VERSION = '3.5'
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
                insideDocker(buildEnv, pipelineContext.getBuildConfig().getDefaultImage(), pipelineContext.getBuildConfig().DOCKER_REGISTRY, pipelineContext.getBuildConfig(), timeoutMinutes, 'MINUTES') {
                    try {
                        makeTarget(pipelineContext) {
                            target = 'build-h2o-3'
                            hasJUnit = false
                            archiveFiles = false
                            makefilePath = pipelineContext.getBuildConfig().MAKEFILE_PATH
                            activatePythonEnv = true
                        }
                        findAutoMLTests(pipelineContext, pipelineContext.getBuildConfig().COMPONENT_PY)
                        makeTarget(pipelineContext) {
                            target = 'test-package-py'
                            hasJUnit = false
                            archiveFiles = false
                            makefilePath = pipelineContext.getBuildConfig().MAKEFILE_PATH
                            activatePythonEnv = true
                        }
                        findAutoMLTests(pipelineContext, pipelineContext.getBuildConfig().COMPONENT_R)
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
                        }
                    } finally {
                        archiveArtifacts "**/*.log, **/out.*, **/*py.out.txt, **/java*out.txt, **/status.*"
                        pipelineContext.getBuildConfig().TEST_PACKAGES_COMPONENTS.each { component ->
                            if (pipelineContext.getBuildConfig().stashComponent(component)) {
                                echo "********* Stash ${component} *********"
                                pipelineContext.getUtils().stashFiles(
                                        this,
                                        pipelineContext.getBuildConfig().getStashNameForTestPackage(component),
                                        "h2o-3/test-package-${component}.zip",
                                        true
                                )
                                if (component == pipelineContext.getBuildConfig().COMPONENT_PY) {
                                    pipelineContext.getUtils().stashXGBoostWheels(this, pipelineContext.getBuildConfig().getCurrentXGBVersion())
                                }
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

/**
 * Finds all AutoML tests for given component and writes them to "tests/${component}unitAutoMLList". Test is considered
 * AutoML-related, if its name contains *automl*.
 * @param pipelineContext
 * @param component component to find AutoML tests for
 */
private def findAutoMLTests(final pipelineContext, final component) {
    final def supportedComponents = [pipelineContext.getBuildConfig().COMPONENT_PY, pipelineContext.getBuildConfig().COMPONENT_R]
    if (!supportedComponents.contains(component)) {
        error "Component ${component} is not supported. Supported components are ${supportedComponents.join(', ')}"
    }
    sh "find h2o-3/h2o-${component}/tests -name '*automl*' -type f -exec basename {} \\; > h2o-3/tests/${component}unitAutoMLList"
}

return this
