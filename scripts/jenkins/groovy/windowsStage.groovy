def call(final pipelineContext, final stageConfig) {
    def makeTargetWindows = load('h2o-3/scripts/jenkins/groovy/makeTargetWindows.groovy')
    def buildEnv = pipelineContext.getBuildConfig().getBuildEnv() + ["PYTHON_VERSION=${stageConfig.pythonVersion}", "R_VERSION=${stageConfig.rVersion}"]

    echo "###### Changes for ${stageConfig.component} detected, starting ${stageConfig.stageName} ######"
    withEnv(buildEnv) {
        timeout(stageConfig.timeoutValue) {
            def h2oFolder = stageConfig.stageDir + '\\h2o-3'

            // pull the test package unless this is a COMPONENT_ANY stage
            if (stageConfig.component != pipelineContext.getBuildConfig().COMPONENT_ANY) {
                pipelineContext.getUtils().unpackTestPackage(
                        this,
                        pipelineContext.getBuildConfig(),
                        stageConfig.component,
                        stageConfig.stageDir,
                        pipelineContext.getBuildConfig().OS_WINDOWS
                )
            }
            // pull aditional test packages
            for (additionalPackage in stageConfig.additionalTestPackages) {
                echo "Pulling additional test-package-${additionalPackage}.zip"
                pipelineContext.getUtils().unpackTestPackage(
                        this,
                        pipelineContext.getBuildConfig(),
                        additionalPackage,
                        stageConfig.stageDir,
                        pipelineContext.getBuildConfig().OS_WINDOWS
                )
            }

            if (stageConfig.component == pipelineContext.getBuildConfig().COMPONENT_PY || stageConfig.additionalTestPackages.contains(pipelineContext.getBuildConfig().COMPONENT_PY)) {
                installPythonPackage(h2oFolder)
                dir(stageConfig.stageDir) {
                    pipelineContext.getUtils().pullXGBWheels(this)
                }
            }

            if (stageConfig.component == pipelineContext.getBuildConfig().COMPONENT_R || stageConfig.additionalTestPackages.contains(pipelineContext.getBuildConfig().COMPONENT_R)) {
                installRPackage(h2oFolder)
            }

            makeTargetWindows(pipelineContext) {
                customBuildAction = stageConfig.customBuildAction
                target = stageConfig.target
                hasJUnit = stageConfig.hasJUnit
                h2o3dir = h2oFolder
                archiveAdditionalFiles = stageConfig.archiveAdditionalFiles
                excludeAdditionalFiles = stageConfig.excludeAdditionalFiles
                makefilePath = stageConfig.makefilePath
                archiveFiles = stageConfig.archiveFiles
                activatePythonEnv = stageConfig.activatePythonEnv
                activateR = stageConfig.activateR
            }
        }
    }
}

def installPythonPackage(String h2o3dir) {
    powershell """
        echo "Activating Python ${env.PYTHON_VERSION}"
        C:\\Users\\jenkins\\h2o-3\\h2o-py${env.PYTHON_VERSION}\\Scripts\\activate.ps1
        python -m pip install \$(ls ${h2o3dir}\\h2o-py\\dist\\*.whl | % {\$_.FullName})
    """
}

def installRPackage(String h2o3dir) {
    powershell """
        R.exe CMD INSTALL \$(ls ${h2o3dir}\\h2o-r\\R\\src\\contrib\\h2o*.tar.gz | % {\$_.FullName})
    """
}

return this
