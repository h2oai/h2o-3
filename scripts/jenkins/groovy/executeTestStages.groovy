def call(buildConfig) {

  def MODE_PR_TESTING_CODE = -1
  def MODE_PR_CODE = 0
  def MODE_MASTER_CODE = 1
  def MODE_NIGHTLY_CODE = 2
  def MODES = [
    [name: 'MODE_PR_TESTING', code: MODE_PR_TESTING_CODE],
    [name: 'MODE_PR', code: MODE_PR_CODE],
    [name: 'MODE_MASTER', code: MODE_MASTER_CODE],
    [name: 'MODE_NIGHTLY', code: MODE_NIGHTLY_CODE]
  ]

  // Job will execute PR_STAGES only if these are green.
  def SMOKE_STAGES = [
    [
      stageName: 'Py2.7 Smoke', target: 'test-py-smoke', pythonVersion: '2.7',
      timeoutValue: 8, lang: buildConfig.LANG_PY
    ],
    [
      stageName: 'R3.4 Smoke', target: 'test-r-smoke', rVersion: '3.4.1',
      timeoutValue: 8, lang: buildConfig.LANG_R
    ],
    [
      stageName: 'PhantomJS Smoke', target: 'test-phantom-js-smoke',
      timeoutValue: 10, lang: buildConfig.LANG_JS
    ],
    [
      stageName: 'Java8 Smoke', target: 'test-junit-smoke',
      timeoutValue: 20, lang: buildConfig.LANG_JAVA
    ]
  ]

  // Stages executed after each push to PR branch.
  def PR_STAGES = [
    [
      stageName: 'Py2.7 Booklets', target: 'test-py-booklets', pythonVersion: '2.7',
      timeoutValue: 40, lang: buildConfig.LANG_PY
    ],
    [
      stageName: 'Py2.7 Demos', target: 'test-py-demos', pythonVersion: '2.7',
      timeoutValue: 15, lang: buildConfig.LANG_PY
    ],
    [
      stageName: 'Py2.7 Init', target: 'test-py-init', pythonVersion: '2.7',
      timeoutValue: 5, hasJUnit: false, lang: buildConfig.LANG_PY
    ],
    [
      stageName: 'Py2.7 Small', target: 'test-pyunit-small', pythonVersion: '2.7',
      timeoutValue: 45, lang: buildConfig.LANG_PY
    ],
    [
      stageName: 'Py3.5 Small', target: 'test-pyunit-small', pythonVersion: '3.5',
      timeoutValue: 45, lang: buildConfig.LANG_PY
    ],
    [
      stageName: 'Py3.6 Small', target: 'test-pyunit-small', pythonVersion: '3.6',
      timeoutValue: 45, lang: buildConfig.LANG_PY
    ],
    [
      stageName: 'R3.4 Init', target: 'test-r-init', rVersion: '3.4.1',
      timeoutValue: 5, hasJUnit: false, lang: buildConfig.LANG_R
    ],
    [
      stageName: 'R3.4 Small', target: 'test-r-small', rVersion: '3.4.1',
      timeoutValue: 90, lang: buildConfig.LANG_R
    ],
    [
      stageName: 'R3.4 Small Client Mode', target: 'test-r-small-client-mode', rVersion: '3.4.1',
      timeoutValue: 120, lang: buildConfig.LANG_R
    ],
    [
      stageName: 'R3.4 CMD Check', target: 'test-r-cmd-check', rVersion: '3.4.1',
      timeoutValue: 15, hasJUnit: false, lang: buildConfig.LANG_R
    ],
    [
      stageName: 'R3.4 CMD Check as CRAN', target: 'test-r-cmd-check-as-cran', rVersion: '3.4.1',
      timeoutValue: 10, hasJUnit: false, lang: buildConfig.LANG_R
    ],
    [
      stageName: 'R3.4 Booklets', target: 'test-r-booklets', rVersion: '3.4.1',
      timeoutValue: 50, lang: buildConfig.LANG_R
    ],
    [
      stageName: 'R3.4 Demos Small', target: 'test-r-demos-small', rVersion: '3.4.1',
      timeoutValue: 15, lang: buildConfig.LANG_R
    ],
    [
      stageName: 'PhantomJS', target: 'test-phantom-js',
      timeoutValue: 45, lang: buildConfig.LANG_JS
    ],
    [
      stageName: 'Py3.6 Medium-large', target: 'test-pyunit-medium-large', pythonVersion: '3.5',
      timeoutValue: 90, lang: buildConfig.LANG_PY
    ],
    [
      stageName: 'R3.4 Medium-large', target: 'test-r-medium-large', rVersion: '3.4.1',
      timeoutValue: 70, lang: buildConfig.LANG_R
    ],
    [
      stageName: 'R3.4 Demos Medium-large', target: 'test-r-demos-medium-large', rVersion: '3.4.1',
      timeoutValue: 120, lang: buildConfig.LANG_R
    ],
    [
      stageName: 'INFO Check', target: 'test-info',
      timeoutValue: 10, lang: buildConfig.LANG_NONE, additionalTestPackages: [buildConfig.LANG_R]
    ],
    [
      stageName: 'Py3.6 Test Demos', target: 'test-demos', pythonVersion: '3.6',
      timeoutValue: 10, lang: buildConfig.LANG_PY
    ],
    [
      stageName: 'Java 8 JUnit', target: 'test-junit', pythonVersion: '2.7',
      timeoutValue: 90, lang: buildConfig.LANG_JAVA, additionalTestPackages: [buildConfig.LANG_PY]
    ]
  ]

  // Stages for PRs in testing phase, executed after each push to PR.
  def PR_TESTING_STAGES = PR_STAGES.findAll{k ->
    // get all stages shorter than 45 minutes and exclude JS stages
    (k['timeoutValue'] <= 45 && k['lang'] != buildConfig.LANG_JS) ||
      // include R Small and Medium-large regardless of previous conditions
      (k['stageName'] == 'R3.4 Medium-large' || k['stageName'] == 'R3.4 Small') ||
        // include JUnit
        (k['lang'] == buildConfig.LANG_JAVA)
  }

  // Stages executed in addition to PR_STAGES after merge to master.
  def MASTER_STAGES = [
    [
      stageName: 'Py2.7 Medium-large', target: 'test-pyunit-medium-large', pythonVersion: '2.7',
      timeoutValue: 90, lang: buildConfig.LANG_PY
    ],
    [
      stageName: 'Py3.5 Medium-large', target: 'test-pyunit-medium-large', pythonVersion: '3.5',
      timeoutValue: 90, lang: buildConfig.LANG_PY
    ],
    [
      stageName: 'R3.4 Datatable', target: 'test-r-datatable', rVersion: '3.4.1',
      timeoutValue: 20, lang: buildConfig.LANG_R
    ],
    [
      stageName: 'PhantomJS Small', target: 'test-phantom-js-small',
      timeoutValue: 45, lang: buildConfig.LANG_JS
    ],
    [
      stageName: 'PhantomJS Medium', target: 'test-phantom-js-medium',
      timeoutValue: 45, lang: buildConfig.LANG_JS
    ]
  ]

  // Stages executed in addition to MASTER_STAGES, used for nightly builds.
  def NIGHTLY_STAGES = [
    [
      stageName: 'R3.3 Medium-large', target: 'test-r-medium-large', rVersion: '3.3.3',
      timeoutValue: 70, lang: buildConfig.LANG_R
    ],
    [
      stageName: 'R3.3 Small', target: 'test-r-small', rVersion: '3.3.3',
      timeoutValue: 90, lang: buildConfig.LANG_R
    ],
    [
      stageName: 'R3.3 Small Client Mode', target: 'test-r-small-client-mode', rVersion: '3.3.3',
      timeoutValue: 90, lang: buildConfig.LANG_R
    ],
    [
      stageName: 'R3.3 CMD Check', target: 'test-r-cmd-check', rVersion: '3.3.3',
      timeoutValue: 15, hasJUnit: false, lang: buildConfig.LANG_R
    ],
    [
      stageName: 'R3.3 CMD Check as CRAN', target: 'test-r-cmd-check-as-cran', rVersion: '3.3.3',
      timeoutValue: 10, hasJUnit: false, lang: buildConfig.LANG_R
    ]
  ]

  // run smoke tests, the tests relevant for this mode
  executeInParallel(SMOKE_STAGES, buildConfig)

  def modeCode = MODES.find{it['name'] == buildConfig.getMode()}['code']
  // FIXME: Remove the if and KEEP only the else once the initial PR tests in real environment are completed
  def jobs = null
  if (modeCode == MODE_PR_TESTING_CODE) {
    jobs = PR_TESTING_STAGES
  } else {
    jobs = PR_STAGES
    if (modeCode >= MODE_MASTER_CODE) {
      jobs += MASTER_STAGES
    }
    if (modeCode >= MODE_NIGHTLY_CODE) {
      jobs += NIGHTLY_STAGES
    }
  }
  executeInParallel(jobs, buildConfig)
}

def executeInParallel(jobs, buildConfig) {
  parallel(jobs.collectEntries { c ->
    [
      c['stageName'], {
        defaultTestPipeline(buildConfig) {
          stageName = c['stageName']
          target = c['target']
          pythonVersion = c['pythonVersion']
          rVersion = c['rVersion']
          timeoutValue = c['timeoutValue']
          hasJUnit = c['hasJUnit']
          lang = c['lang']
          additionalTestPackages = c['additionalTestPackages']
          nodeLabel = c['nodeLabel']
        }
      }
    ]
  })
}

def defaultTestPipeline(buildConfig, body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  if (config.pythonVersion == null) {
    config.pythonVersion = '3.5'
  }
  if (config.rVersion == null) {
    config.rVersion = '3.4.1'
  }
  if (config.timeoutValue == null) {
    config.timeoutValue = 60
  }
  if (config.hasJUnit == null) {
    config.hasJUnit = true
  }
  if (config.additionalTestPackages == null) {
    config.additionalTestPackages = []
  }
  if (config.nodeLabel == null) {
    config.nodeLabel = buildConfig.getNodeLabel()
  }

  node(config.nodeLabel) {
    echo "###### Pulling scripts. ######"
    step ([$class: 'CopyArtifact',
      projectName: env.JOB_NAME,
      filter: "h2o-3/scripts/jenkins/groovy/*",
      selector: [$class: 'SpecificBuildSelector', buildNumber: env.BUILD_ID]
    ]);

    def insideDocker = load('h2o-3/scripts/jenkins/groovy/insideDocker.groovy')
    def buildTarget = load('h2o-3/scripts/jenkins/groovy/buildTarget.groovy')
    def customEnv = load('h2o-3/scripts/jenkins/groovy/customEnv.groovy')

    def buildEnv = customEnv() + ["PYTHON_VERSION=${config.pythonVersion}", "R_VERSION=${config.rVersion}"]

    insideDocker(buildEnv, buildConfig, config.timeoutValue, 'MINUTES') {
      // NOTES regarding changes detection and rerun:
      // An empty stage is a stage which is created, but does not execute any tests.
      // Consider following scenario:
      //  commit 1 - only Py files are changed -> only Py stages are created
      //           - if we have created the empty R stages as well,
      //             they are marked as SUCCESFUL (no tests -> (almost) nothing to fail)
      //  commit 2 - we add some R changes and we use rerun -> Py stages are skipped, they were successful in previous build
      //           - however, if we had created the empty R stages,
      //             they will be skipped as well, because they are marked as SUCESSFUL in previous build
      // This is why the stages for not changed langs must NOT be created.
      // On the other hand, empty stages for those being reran must be created.
      // Otherwise the rerun mechanism will not be able to distinguish if the
      // stage is missing in previous build  because it was skipped due to the
      // change detection (and it should be run in this build) or because it was
      // skipped due to the rerun (and it shouldn't be run in this build either).

      // run stage only if there is something changed for this or relevant lang.
      if (buildConfig.langChanged(config.lang)) {
        echo "###### Changes for ${config.lang} detected, starting ${config.stageName} ######"
        stage(config.stageName) {
          // run tests only if all stages should be run or if this stage was FAILED in previous build
          if(runAllStages(buildConfig) || !wasStageSuccessful(config.stageName)) {
            echo "###### ${config.stageName} was not successful or was not executed in previous build, executing it now. ######"

            def stageDir = stageNameToDirName(config.stageName)
            def h2oFolder = stageDir + '/h2o-3'
            dir(stageDir) {
              deleteDir()
            }

            // pull the test package unless this is a LANG_NONE stage
            if (config.lang != buildConfig.LANG_NONE) {
              unpackTestPackage(config.lang, stageDir)
            }
            // pull aditional test packages
            for (additionalPackage in config.additionalTestPackages) {
              echo "Pulling additional test-package-${additionalPackage}.zip"
              unpackTestPackage(additionalPackage, stageDir)
            }

            if (config.lang == buildConfig.LANG_PY || config.additionalTestPackages.contains(buildConfig.LANG_PY)) {
              installPythonPackage(h2oFolder)
            }

            if (config.lang == buildConfig.LANG_R || config.additionalTestPackages.contains(buildConfig.LANG_R)) {
              installRPackage(h2oFolder)
            }

            buildTarget {
              target = config.target
              hasJUnit = config.hasJUnit
              h2o3dir = h2oFolder
            }
          } else {
            echo "###### ${config.stageName} was successful in previous build, skipping it in this build because RERUN FAILED STAGES is enabled. ######"
          }
        }
      } else {
        echo "###### Changes for ${config.lang} NOT detected, skipping ${config.stageName}. ######"
      }
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

def unpackTestPackage(lang, String stageDir) {
  echo "###### Pulling test package. ######"
  step ([$class: 'CopyArtifact',
    projectName: env.JOB_NAME,
    fingerprintArtifacts: true,
    filter: "h2o-3/test-package-${lang}.zip, h2o-3/build/h2o.jar",
    selector: [$class: 'SpecificBuildSelector', buildNumber: env.BUILD_ID],
    target: stageDir + '/'
  ]);
  sh "cd ${stageDir}/h2o-3 && unzip -o test-package-${lang}.zip && rm test-package-${lang}.zip"
}

def stageNameToDirName(String stageName) {
  if (stageName != null) {
    return stageName.toLowerCase().replace(' ', '-')
  }
  return null
}

def runAllStages(buildConfig) {
    // first check the commit message contains !rerun token, if yes, then don't run all stages,
    // if not, then run all stages
    def result = !buildConfig.commitMessageContains('!rerun')
    // if we shouldn't run all stages based on the commit message, check
    // that this is not overriden by environment
    if (!result) {
      result = env.overrideRerun == null || env.overrideRerun.toLowerCase() == 'true'
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
