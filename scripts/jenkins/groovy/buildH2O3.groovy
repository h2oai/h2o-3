def call(final pipelineContext) {

    def PYTHON_VERSION = '3.5'
    def R_VERSION = '3.4.1'

    // Load required scripts
    def insideDocker = load('h2o-3/scripts/jenkins/groovy/insideDocker.groovy')
    def makeTarget = load('h2o-3/scripts/jenkins/groovy/makeTarget.groovy')

    def stageName = 'Build H2O-3'

    pipelineContext.getBuildSummary().addStageSummary(this, stageName, 'h2o-3')
    pipelineContext.getBuildSummary().setStageDetails(this, stageName, env.NODE_NAME, env.WORKSPACE)
    try {
        // Launch docker container, build h2o-3, create test packages and archive artifacts
        def buildEnv = pipelineContext.getBuildConfig().getBuildEnv() + "PYTHON_VERSION=${PYTHON_VERSION}" + "R_VERSION=${R_VERSION}"
        def timeoutMinutes = pipelineContext.getBuildConfig().getBuildHadoop() ? 50 : 15
        stage(stageName) {
            insideDocker(buildEnv, pipelineContext.getBuildConfig().DEFAULT_IMAGE, pipelineContext.getBuildConfig().DOCKER_REGISTRY, pipelineContext.getBuildConfig(), timeoutMinutes, 'MINUTES') {
                try {
                    makeTarget(pipelineContext) {
                        target = 'build-h2o-3'
                        hasJUnit = false
                        archiveFiles = false
                        makefilePath = pipelineContext.getBuildConfig().MAKEFILE_PATH
                    }
                    findAutoMLTests(pipelineContext, pipelineContext.getBuildConfig().COMPONENT_PY)
                    findXGBoostTests(pipelineContext, pipelineContext.getBuildConfig().COMPONENT_PY)
                    makeTarget(pipelineContext) {
                        target = 'test-package-py'
                        hasJUnit = false
                        archiveFiles = false
                        makefilePath = pipelineContext.getBuildConfig().MAKEFILE_PATH
                    }
                    findAutoMLTests(pipelineContext, pipelineContext.getBuildConfig().COMPONENT_R)
                    findXGBoostTests(pipelineContext, pipelineContext.getBuildConfig().COMPONENT_R)
                    makeTarget(pipelineContext) {
                        target = 'test-package-r'
                        hasJUnit = false
                        archiveFiles = false
                        makefilePath = pipelineContext.getBuildConfig().MAKEFILE_PATH
                    }
                    if (pipelineContext.getBuildConfig().getBuildHadoop()) {
                        makeTarget(pipelineContext) {
                            target = 'test-package-hadoop'
                            hasJUnit = false
                            archiveFiles = false
                            makefilePath = pipelineContext.getBuildConfig().MAKEFILE_PATH
                        }
                    }
                    if (pipelineContext.getBuildConfig().componentChanged(pipelineContext.getBuildConfig().COMPONENT_JS)) {
                        makeTarget(pipelineContext) {
                            target = 'test-package-js'
                            hasJUnit = false
                            archiveFiles = false
                            makefilePath = pipelineContext.getBuildConfig().MAKEFILE_PATH
                        }
                    }
                    if (pipelineContext.getBuildConfig().componentChanged(pipelineContext.getBuildConfig().COMPONENT_JAVA)) {
                        makeTarget(pipelineContext) {
                            target = 'test-package-java'
                            hasJUnit = false
                            archiveFiles = false
                            makefilePath = pipelineContext.getBuildConfig().MAKEFILE_PATH
                        }
                    }
                } finally {
                    archiveArtifacts """
                          h2o-3/${pipelineContext.getBuildConfig().MAKEFILE_PATH},
                          h2o-3/h2o-py/dist/*.whl,
                          h2o-3/build/h2o.jar,
                          h2o-3/h2o-3/src/contrib/h2o_*.tar.gz,
                          h2o-3/h2o-assemblies/genmodel/build/libs/genmodel.jar,
                          **/*.log, **/out.*, **/*py.out.txt, **/java*out.txt, **/tests.txt, **/status.*
                    """
                    pipelineContext.getBuildConfig().TEST_PACKAGES_COMPONENTS.each { component ->
                        if (pipelineContext.getBuildConfig().componentChanged(component)) {
                            echo "********* Stash ${component} *********"
                            pipelineContext.getUtils().stashFiles(
                                    this,
                                    pipelineContext.getBuildConfig().getStashNameForTestPackage(component),
                                    "h2o-3/test-package-${component}.zip"
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

/**
 * Finds all AutoML tests for given component and writes them to "tests/${component}unitAutoMLList". Test is considered
 * AutoML-related, if its name contains *automl*.
 * @param pipelineContext
 * @param component component to find AutoML tests for,
 */
private def findAutoMLTests(final pipelineContext, final component) {
    final def supportedComponents = [pipelineContext.getBuildConfig().COMPONENT_PY, pipelineContext.getBuildConfig().COMPONENT_R]
    if (!supportedComponents.contains(component)) {
        error "Component ${component} is not supported. Supported components are ${supportedComponents.join(', ')}"
    }
    sh "find h2o-3/h2o-${component}/tests -name '*automl*' -type f -exec basename {} \\; > h2o-3/tests/${component}unitAutoMLList"
}

/**
 * Finds all XGBoost tests for given component and writes them to "tests/${component}unitXGBoostList". Test is considered
 * XGBoost-related, if its path or name contains *xgboost*.
 * @param pipelineContext
 * @param component component to find XGBoost tests for,
 */
private def findXGBoostTests(final pipelineContext, final component) {
    final def supportedComponents = [pipelineContext.getBuildConfig().COMPONENT_PY, pipelineContext.getBuildConfig().COMPONENT_R]
    if (!supportedComponents.contains(component)) {
        error "Component ${component} is not supported. Supported components are ${supportedComponents.join(', ')}"
    }
    sh "find h2o-3/h2o-${component}/tests -wholename '*xgboost*' -type f -exec basename {} \\; > h2o-3/tests/${component}unitXGBoostList"
}

return this
