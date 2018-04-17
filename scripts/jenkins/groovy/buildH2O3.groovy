def call(final pipelineContext) {

    def PYTHON_VERSION = '3.5'
    def R_VERSION = '3.4.1'

    // Load required scripts
    def insideDocker = load('h2o-3/scripts/jenkins/groovy/insideDocker.groovy')
    def makeTarget = load('h2o-3/scripts/jenkins/groovy/makeTarget.groovy')

    def stageName = 'Build H2O-3'

    withCustomCommitStates(scm, pipelineContext.getBuildConfig().H2O_OPS_TOKEN, "${pipelineContext.getBuildConfig().getGitHubCommitStateContext(stageName)}") {
        pipelineContext.getBuildSummary().addStageSummary(this, stageName, 'h2o-3')
        pipelineContext.getBuildSummary().setStageDetails(this, stageName, env.NODE_NAME, env.WORKSPACE)
        try {
            // Launch docker container, build h2o-3, create test packages and archive artifacts
            def buildEnv = pipelineContext.getBuildConfig().getBuildEnv() + "PYTHON_VERSION=${PYTHON_VERSION}" + "R_VERSION=${R_VERSION}"
            def timeoutMinutes = pipelineContext.getBuildConfig().getBuildHadoop() ? 40 : 15
            stage(stageName) {
                insideDocker(buildEnv, pipelineContext.getBuildConfig().DEFAULT_IMAGE, pipelineContext.getBuildConfig().DOCKER_REGISTRY, pipelineContext.getBuildConfig(), timeoutMinutes, 'MINUTES') {
                    try {
                        makeTarget(pipelineContext) {
                            target = 'build-h2o-3'
                            hasJUnit = false
                            archiveFiles = false
                            makefilePath = pipelineContext.getBuildConfig().MAKEFILE_PATH
                        }
                        makeTarget(pipelineContext) {
                            target = 'test-package-py'
                            hasJUnit = false
                            archiveFiles = false
                            makefilePath = pipelineContext.getBuildConfig().MAKEFILE_PATH
                        }
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
}

return this
