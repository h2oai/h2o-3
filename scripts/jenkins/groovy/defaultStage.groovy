def call(final pipelineContext, final stageConfig) {
    def insideDocker = load('h2o-3/scripts/jenkins/groovy/insideDocker.groovy')
    def makeTarget = load('h2o-3/scripts/jenkins/groovy/makeTarget.groovy')

    def buildEnv = pipelineContext.getBuildConfig().getBuildEnv() + ["PYTHON_VERSION=${stageConfig.pythonVersion}", "R_VERSION=${stageConfig.rVersion}", "JAVA_VERSION=${stageConfig.javaVersion}"]

    echo "###### Changes for ${stageConfig.component} detected, starting ${stageConfig.stageName} ######"
    insideDocker(buildEnv, stageConfig.image, pipelineContext.getBuildConfig().DOCKER_REGISTRY, pipelineContext.getBuildConfig(), stageConfig.timeoutValue, 'MINUTES', stageConfig.customDockerArgs.join(' '), stageConfig.addToDockerGroup, stageConfig.awsCredsPrefix) {
        def h2oFolder = stageConfig.stageDir + '/h2o-3'

        pipelineContext.getUtils().unpackTestPackage(this, pipelineContext.getBuildConfig(), stageConfig.component, stageConfig.stageDir)
        // pull additional test packages
        for (additionalPackage in stageConfig.additionalTestPackages) {
            echo "Pulling additional test-package-${additionalPackage}.zip"
            pipelineContext.getUtils().unpackTestPackage(this, pipelineContext.getBuildConfig(), additionalPackage, stageConfig.stageDir)
        }

        if (stageConfig.component == pipelineContext.getBuildConfig().COMPONENT_PY || stageConfig.additionalTestPackages.contains(pipelineContext.getBuildConfig().COMPONENT_PY)) {
            installPythonPackage(h2oFolder)
            // Install xgboost wheels only on python 3.7 and 3.8
            // FIXME - legacy code, the xgboost wheels should not be used -> no need to install them
            String[] pythonVersionFull = stageConfig.pythonVersion.split("\\.")
            int pythonMajor = pythonVersionFull[0] as Integer
            int pythonMinor = pythonVersionFull[1] as Integer
            if (pythonMajor == 3 && (pythonMinor == 7 || pythonMinor == 8)) { // For some reason python 3.9 is not supported, probably is not the wheele is not build for 3.9
                dir(stageConfig.stageDir) {
                    pipelineContext.getUtils().pullXGBWheels(this)
                }
                installXGBWheel(h2oFolder)
            }
        }

        if (stageConfig.component == pipelineContext.getBuildConfig().COMPONENT_PY) {
            writeFile(
                    file: "${h2oFolder}/tests/pyunitChangedTestList", 
                    text: pipelineContext.getBuildConfig().getChangedPythonTests().join("\n")
            )
        }

        if (stageConfig.installRPackage && (stageConfig.component == pipelineContext.getBuildConfig().COMPONENT_R || stageConfig.additionalTestPackages.contains(pipelineContext.getBuildConfig().COMPONENT_R))) {
            installRPackage(h2oFolder)
        }

        makeTarget(pipelineContext) {
            preBuildAction = stageConfig.preBuildAction
            customBuildAction = stageConfig.customBuildAction
            postSuccessfulBuildAction = stageConfig.postSuccessfulBuildAction
            postFailedBuildAction = stageConfig.postFailedBuildAction
            postSuccessfulBuildAction = stageConfig.postSuccessfulBuildAction
            postBuildAction = stageConfig.postBuildAction
            target = stageConfig.target
            hasJUnit = stageConfig.hasJUnit
            h2o3dir = h2oFolder
            archiveAdditionalFiles = stageConfig.archiveAdditionalFiles
            excludeAdditionalFiles = stageConfig.excludeAdditionalFiles
            makefilePath = stageConfig.makefilePath
            archiveFiles = stageConfig.archiveFiles
            activatePythonEnv = stageConfig.activatePythonEnv
            javaVersion = stageConfig.javaVersion
        }
    }
}

def installPythonPackage(String h2o3dir) {
    sh """
        echo "Activating Python ${env.PYTHON_VERSION}"
        . /envs/h2o_env_python${env.PYTHON_VERSION}/bin/activate
        pip install --no-dependencies ${h2o3dir}/h2o-py/build/dist/*.whl
    """
}

def installRPackage(String h2o3dir) {
    sh """
        R CMD INSTALL ${h2o3dir}/h2o-r/R/src/contrib/h2o*.tar.gz
    """
}

def installXGBWheel(final String h2o3dir) {
    sh """
        echo "Activating Python ${env.PYTHON_VERSION}"
        . /envs/h2o_env_python${env.PYTHON_VERSION}/bin/activate
            pip install ${h2o3dir}/xgb-whls/xgboost_ompv4-*-cp${env.PYTHON_VERSION.replaceAll('\\.','')}-*-linux_x86_64.whl
    """
}

return this
