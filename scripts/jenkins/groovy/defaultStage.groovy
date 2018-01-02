def call(final pipelineContext, final stageConfig) {
  def insideDocker = load('h2o-3/scripts/jenkins/groovy/insideDocker.groovy')
  def makeTarget = load('h2o-3/scripts/jenkins/groovy/makeTarget.groovy')

  def buildEnv = pipelineContext.getBuildConfig().getBuildEnv() + ["PYTHON_VERSION=${stageConfig.pythonVersion}", "R_VERSION=${stageConfig.rVersion}"]

  insideDocker(buildEnv, stageConfig.image, pipelineContext.getBuildConfig().DOCKER_REGISTRY, stageConfig.timeoutValue, 'MINUTES') {
    // NOTES regarding changes detection and rerun:
    // An empty stage is a stage which is created, but does not execute any tests.
    // Consider following scenario:
    //  commit 1 - only Py files are changed -> only Py stages are created
    //           - if we have created the empty R stages as well,
    //             they are marked as SUCCESFUL (no tests -> (almost) nothing to fail)
    //  commit 2 - we add some R changes and we use rerun -> Py stages are skipped, they were successful in previous build
    //           - however, if we had created the empty R stages,
    //             they will be skipped as well, because they are marked as SUCESSFUL in previous build
    // This is why the stages for not changed components must NOT be created.
    // On the other hand, empty stages for those being reran must be created.
    // Otherwise the rerun mechanism will not be able to distinguish if the
    // stage is missing in previous build  because it was skipped due to the
    // change detection (and it should be run in this build) or because it was
    // skipped due to the rerun (and it shouldn't be run in this build either).

    // run stage only if there is something changed for this or relevant component.
    if (pipelineContext.getBuildConfig().componentChanged(stageConfig.component)) {
      echo "###### Changes for ${stageConfig.component} detected, starting ${stageConfig.stageName} ######"
      stage(stageConfig.stageName) {
        // run tests only if all stages should be run or if this stage was FAILED in previous build
        if (runAllStages(pipelineContext) || !wasStageSuccessful(stageConfig.stageName)) {
          echo "###### ${stageConfig.stageName} was not successful or was not executed in previous build, executing it now. ######"

          def h2oFolder = stageConfig.stageDir + '/h2o-3'

          // pull the test package unless this is a COMPONENT_ANY stage
          if (stageConfig.component != pipelineContext.getBuildConfig().COMPONENT_ANY) {
            unpackTestPackage(stageConfig.component, stageConfig.stageDir)
          }
          // pull aditional test packages
          for (additionalPackage in stageConfig.additionalTestPackages) {
            echo "Pulling additional test-package-${additionalPackage}.zip"
            unpackTestPackage(additionalPackage, stageConfig.stageDir)
          }

          if (stageConfig.component == pipelineContext.getBuildConfig().COMPONENT_PY || stageConfig.additionalTestPackages.contains(pipelineContext.getBuildConfig().COMPONENT_PY)) {
            installPythonPackage(h2oFolder)
          }

          if (stageConfig.component == pipelineContext.getBuildConfig().COMPONENT_R || stageConfig.additionalTestPackages.contains(pipelineContext.getBuildConfig().COMPONENT_R)) {
            installRPackage(h2oFolder)
          }

          makeTarget {
            target = stageConfig.target
            hasJUnit = stageConfig.hasJUnit
            h2o3dir = h2oFolder
            archiveAdditionalFiles = stageConfig.archiveAdditionalFiles
            makefilePath = stageConfig.makefilePath
          }
        } else {
          echo "###### ${stageConfig.stageName} was successful in previous build, skipping it in this build because RERUN FAILED STAGES is enabled. ######"
        }
      }
    } else {
      echo "###### Changes for ${stageConfig.component} NOT detected, skipping ${stageConfig.stageName}. ######"
    }
  }
}

def installPythonPackage(String h2o3dir) {
  sh """
    echo "Activating Python ${env.PYTHON_VERSION}"
    . /envs/h2o_env_python${env.PYTHON_VERSION}/bin/activate
    pip install ${h2o3dir}/h2o-py/dist/*.whl
  """
}

def installRPackage(String h2o3dir) {
  sh """
    echo "Activating R ${env.R_VERSION}"
    activate_R_${env.R_VERSION}
    R CMD INSTALL ${h2o3dir}/h2o-r/R/src/contrib/h2o*.tar.gz
  """
}

def unpackTestPackage(component, String stageDir) {
  echo "###### Pulling test package. ######"
  step ([$class: 'CopyArtifact',
    projectName: env.JOB_NAME,
    fingerprintArtifacts: true,
    filter: "h2o-3/test-package-${component}.zip, h2o-3/build/h2o.jar",
    selector: [$class: 'SpecificBuildSelector', buildNumber: env.BUILD_ID],
    target: stageDir + '/'
  ])
  sh "cd ${stageDir}/h2o-3 && unzip -q -o test-package-${component}.zip && rm test-package-${component}.zip"
}

def runAllStages(final pipelineContext) {
    // first check the commit message contains !rerun token, if yes, then don't run all stages,
    // if not, then run all stages
    def result = !pipelineContext.getBuildConfig().commitMessageContains('!rerun')
    // if we shouldn't run all stages based on the commit message, check
    // that this is not overridden by environment
    if (!result) {
      result = env.ignoreRerun == null || env.ignoreRerun.toLowerCase() == 'true'
    }
    if (result) {
      echo "###### RERUN NOT ENABLED, will execute all stages ######"
    } else {
      echo "###### RERUN ENABLED, will execute only stages which failed in previous build ######"
    }
    return result
}

@NonCPS
def wasStageSuccessful(String stageName) {
  // displayName of the relevant end node.
  def STAGE_END_TYPE_DISPLAY_NAME = 'Stage : Body : End'

  // There is no previous build, the stage cannot be successful.
  if (currentBuild.previousBuild == null) {
    echo "###### No previous build available, marking ${stageName} as FAILED. ######"
    return false
  }

  // Get all nodes in previous build.
  def prevBuildNodes = currentBuild.previousBuild.rawBuild
    .getAction(org.jenkinsci.plugins.workflow.job.views.FlowGraphAction.class)
    .getNodes()
  // Get all end nodes of the relevant stage in previous build. We need to check
  // the end nodes, because errors are being recorded on the end nodes.
  def stageEndNodesInPrevBuild = prevBuildNodes.findAll{it.getTypeDisplayName() == STAGE_END_TYPE_DISPLAY_NAME}
    .findAll{it.getStartNode().getDisplayName() == stageName}

  // If there is no start node for this stage in previous build that means the
  // stage was not present in previous build, therefore the stage cannot be successful.
  def stageMissingInPrevBuild = stageEndNodesInPrevBuild.isEmpty()
  if (stageMissingInPrevBuild) {
    echo "###### ${stageName} not present in previous build, marking this stage as FAILED. ######"
    return false
  }

  // If the list of end nodes for this stage having error is empty, that
  // means the stage was successful. The errors are being recorded on the end nodes.
  return stageEndNodesInPrevBuild.find{it.getError() != null} == null
}

return this
