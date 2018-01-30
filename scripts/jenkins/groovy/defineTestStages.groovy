def call(final pipelineContext) {

  def MODE_PR_CODE = 0
  def MODE_BENCHMARK_CODE = 1
  def MODE_HADOOP_CODE = 2
  def MODE_MASTER_CODE = 10
  def MODE_NIGHTLY_CODE = 20
  def MODES = [
    [name: 'MODE_PR', code: MODE_PR_CODE],
    [name: 'MODE_HADOOP', code: MODE_HADOOP_CODE],
    [name: 'MODE_BENCHMARK', code: MODE_BENCHMARK_CODE],
    [name: 'MODE_MASTER', code: MODE_MASTER_CODE],
    [name: 'MODE_NIGHTLY', code: MODE_NIGHTLY_CODE]
  ]

  // Job will execute PR_STAGES only if these are green.
  def SMOKE_STAGES = [
    [
      stageName: 'Py2.7 Smoke', target: 'test-py-smoke', pythonVersion: '2.7',timeoutValue: 8,
      component: pipelineContext.getBuildConfig().COMPONENT_PY
    ],
    [
      stageName: 'R3.4 Smoke', target: 'test-r-smoke', rVersion: '3.4.1',timeoutValue: 8,
      component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'PhantomJS Smoke', target: 'test-phantom-js-smoke',timeoutValue: 20,
      component: pipelineContext.getBuildConfig().COMPONENT_JS
    ],
    [
      stageName: 'Java8 Smoke', target: 'test-junit-smoke',timeoutValue: 20,
      component: pipelineContext.getBuildConfig().COMPONENT_JAVA
    ]
  ]

  // Stages executed after each push to PR branch.
  def PR_STAGES = [
    [
      stageName: 'Py2.7 Booklets', target: 'test-py-booklets', pythonVersion: '2.7',
      timeoutValue: 40, component: pipelineContext.getBuildConfig().COMPONENT_PY
    ],
    [
      stageName: 'Py2.7 Demos', target: 'test-py-demos', pythonVersion: '2.7',
      timeoutValue: 30, component: pipelineContext.getBuildConfig().COMPONENT_PY
    ],
    [
      stageName: 'Py2.7 Init', target: 'test-py-init', pythonVersion: '2.7',
      timeoutValue: 5, hasJUnit: false, component: pipelineContext.getBuildConfig().COMPONENT_PY
    ],
    [
      stageName: 'Py2.7 Small', target: 'test-pyunit-small', pythonVersion: '2.7',
      timeoutValue: 90, component: pipelineContext.getBuildConfig().COMPONENT_PY
    ],
    [
      stageName: 'Py3.5 Small', target: 'test-pyunit-small', pythonVersion: '3.5',
      timeoutValue: 90, component: pipelineContext.getBuildConfig().COMPONENT_PY
    ],
    [
      stageName: 'Py3.6 Small', target: 'test-pyunit-small', pythonVersion: '3.6',
      timeoutValue: 90, component: pipelineContext.getBuildConfig().COMPONENT_PY
    ],
    [
      stageName: 'R3.4 Init', target: 'test-r-init', rVersion: '3.4.1',
      timeoutValue: 5, hasJUnit: false, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'R3.4 Small', target: 'test-r-small', rVersion: '3.4.1',
      timeoutValue: 110, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'R3.4 Small Client Mode', target: 'test-r-small-client-mode', rVersion: '3.4.1',
      timeoutValue: 140, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'R3.4 CMD Check', target: 'test-r-cmd-check', rVersion: '3.4.1',
      timeoutValue: 15, hasJUnit: false, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'R3.4 CMD Check as CRAN', target: 'test-r-cmd-check-as-cran', rVersion: '3.4.1',
      timeoutValue: 10, hasJUnit: false, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'R3.4 Booklets', target: 'test-r-booklets', rVersion: '3.4.1',
      timeoutValue: 50, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'R3.4 Demos Small', target: 'test-r-demos-small', rVersion: '3.4.1',
      timeoutValue: 15, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'PhantomJS', target: 'test-phantom-js',
      timeoutValue: 75, component: pipelineContext.getBuildConfig().COMPONENT_JS
    ],
    [
      stageName: 'Py3.6 Medium-large', target: 'test-pyunit-medium-large', pythonVersion: '3.5',
      timeoutValue: 120, component: pipelineContext.getBuildConfig().COMPONENT_PY
    ],
    [
      stageName: 'R3.4 Medium-large', target: 'test-r-medium-large', rVersion: '3.4.1',
      timeoutValue: 70, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'R3.4 Demos Medium-large', target: 'test-r-demos-medium-large', rVersion: '3.4.1',
      timeoutValue: 120, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'INFO Check', target: 'test-info',
      timeoutValue: 10, component: pipelineContext.getBuildConfig().COMPONENT_ANY, additionalTestPackages: [pipelineContext.getBuildConfig().COMPONENT_R]
    ],
    [
      stageName: 'Py3.6 Test Demos', target: 'test-demos', pythonVersion: '3.6',
      timeoutValue: 10, component: pipelineContext.getBuildConfig().COMPONENT_PY
    ],
    [
      stageName: 'Java 8 JUnit', target: 'test-junit-jenkins', pythonVersion: '2.7',
      timeoutValue: 90, component: pipelineContext.getBuildConfig().COMPONENT_JAVA, additionalTestPackages: [pipelineContext.getBuildConfig().COMPONENT_PY]
    ]
  ]

  def BENCHMARK_STAGES = [
    [
      stageName: 'GBM Benchmark', executionScript: 'h2o-3/scripts/jenkins/groovy/benchmarkStage.groovy',
      timeoutValue: 120, target: 'benchmark', component: pipelineContext.getBuildConfig().COMPONENT_ANY,
      additionalTestPackages: [pipelineContext.getBuildConfig().COMPONENT_R], image: pipelineContext.getBuildConfig().BENCHMARK_IMAGE,
      nodeLabel: pipelineContext.getBuildConfig().getBenchmarkNodeLabel(), customData: [model: 'gbm'], makefilePath: pipelineContext.getBuildConfig().BENCHMARK_MAKEFILE_PATH
    ]
  ]

  // Stages executed in addition to PR_STAGES after merge to master.
  def MASTER_STAGES = [
    [
      stageName: 'Py2.7 Medium-large', target: 'test-pyunit-medium-large', pythonVersion: '2.7',
      timeoutValue: 120, component: pipelineContext.getBuildConfig().COMPONENT_PY
    ],
    [
      stageName: 'Py3.5 Medium-large', target: 'test-pyunit-medium-large', pythonVersion: '3.5',
      timeoutValue: 120, component: pipelineContext.getBuildConfig().COMPONENT_PY
    ],
    [
      stageName: 'R3.4 Datatable', target: 'test-r-datatable', rVersion: '3.4.1',
      timeoutValue: 40, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'PhantomJS Small', target: 'test-phantom-js-small',
      timeoutValue: 75, component: pipelineContext.getBuildConfig().COMPONENT_JS
    ],
    [
      stageName: 'PhantomJS Medium', target: 'test-phantom-js-medium',
      timeoutValue: 75, component: pipelineContext.getBuildConfig().COMPONENT_JS
    ]
  ]
  MASTER_STAGES += BENCHMARK_STAGES

  // Stages executed in addition to MASTER_STAGES, used for nightly builds.
  def NIGHTLY_STAGES = [
    [
      stageName: 'R3.3 Medium-large', target: 'test-r-medium-large', rVersion: '3.3.3',
      timeoutValue: 70, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'R3.3 Small', target: 'test-r-small', rVersion: '3.3.3',
      timeoutValue: 110, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'R3.3 Small Client Mode', target: 'test-r-small-client-mode', rVersion: '3.3.3',
      timeoutValue: 140, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'R3.3 CMD Check', target: 'test-r-cmd-check', rVersion: '3.3.3',
      timeoutValue: 15, hasJUnit: false, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'R3.3 CMD Check as CRAN', target: 'test-r-cmd-check-as-cran', rVersion: '3.3.3',
      timeoutValue: 10, hasJUnit: false, component: pipelineContext.getBuildConfig().COMPONENT_R
    ]
  ]

  def HADOOP_STAGES = []
  for (distribution in pipelineContext.getBuildConfig().getSupportedHadoopDistributions()) {
    HADOOP_STAGES += [
      stageName: "${distribution.name.toUpperCase()} ${distribution.version} Smoke", target: 'test-hadoop-smoke',
      timeoutValue: 15, component: pipelineContext.getBuildConfig().COMPONENT_ANY,
      additionalTestPackages: [pipelineContext.getBuildConfig().COMPONENT_HADOOP, pipelineContext.getBuildConfig().COMPONENT_PY],
      customData: [distribution: distribution.name, version: distribution.version], pythonVersion: '2.7',
      executionScript: 'h2o-3/scripts/jenkins/groovy/hadoopStage.groovy'
    ]
  }

  def modeCode = MODES.find{it['name'] == pipelineContext.getBuildConfig().getMode()}['code']
  if (modeCode == MODE_BENCHMARK_CODE) {
    executeInParallel(BENCHMARK_STAGES, pipelineContext)
  } else if (modeCode == MODE_HADOOP_CODE) {
    executeInParallel(HADOOP_STAGES, pipelineContext)
  } else {
    executeInParallel(SMOKE_STAGES, pipelineContext)
    def jobs = PR_STAGES
    if (modeCode >= MODE_MASTER_CODE) {
      jobs += MASTER_STAGES
    }
    if (modeCode >= MODE_NIGHTLY_CODE) {
      jobs += NIGHTLY_STAGES
    }
    executeInParallel(jobs, pipelineContext)
  }
}

private void executeInParallel(final jobs, final pipelineContext) {
  parallel(jobs.collectEntries { c ->
    [
      c['stageName'], {
        invokeStage(pipelineContext) {
          stageName = c['stageName']
          target = c['target']
          pythonVersion = c['pythonVersion']
          rVersion = c['rVersion']
          timeoutValue = c['timeoutValue']
          hasJUnit = c['hasJUnit']
          component = c['component']
          additionalTestPackages = c['additionalTestPackages']
          nodeLabel = c['nodeLabel']
          executionScript = c['executionScript']
          image = c['image']
          customData = c['customData']
          makefilePath = c['makefilePath']
          archiveAdditionalFiles = c['archiveAdditionalFiles']
          excludeAdditionalFiles = c['excludeAdditionalFiles']
        }
      }
    ]
  })
}

private void invokeStage(final pipelineContext, final body) {

  def DEFAULT_PYTHON = '3.5'
  def DEFAULT_R = '3.4.1'
  def DEFAULT_TIMEOUT = 60
  def DEFAULT_EXECUTION_SCRIPT = 'h2o-3/scripts/jenkins/groovy/defaultStage.groovy'

  def config = [:]

  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  config.stageDir = pipelineContext.getUtils().stageNameToDirName(config.stageName)

  config.pythonVersion = config.pythonVersion ?: DEFAULT_PYTHON
  config.rVersion = config.rVersion ?: DEFAULT_R
  config.timeoutValue = config.timeoutValue ?: DEFAULT_TIMEOUT
  config.hasJUnit = config.hasJUnit ?: true
  config.additionalTestPackages = config.additionalTestPackages ?: []
  config.nodeLabel = config.nodeLabel ?: pipelineContext.getBuildConfig().getDefaultNodeLabel()
  config.executionScript = config.executionScript ?: DEFAULT_EXECUTION_SCRIPT
  config.image = config.image ?: pipelineContext.getBuildConfig().DEFAULT_IMAGE
  config.makefilePath = config.makefilePath ?: pipelineContext.getBuildConfig().MAKEFILE_PATH
  config.archiveAdditionalFiles = config.archiveAdditionalFiles ?: []
  config.excludeAdditionalFiles = config.excludeAdditionalFiles ?: []

  if (pipelineContext.getBuildConfig().componentChanged(config.component)) {
    pipelineContext.getBuildSummary().addStageSummary(this, config.stageName, config.stageDir)
    stage(config.stageName) {
      if (params.executeFailedOnly && pipelineContext.getUtils().wasStageSuccessful(this, config.stageName)) {
        echo "###### Stage was successful in previous build ######"
        pipelineContext.getBuildSummary().setStageDetails(this, config.stageName, 'Skipped', 'N/A')
        pipelineContext.getBuildSummary().markStageSuccessful(this, config.stageName)
      } else {
        withCustomCommitStates(scm, 'h2o-ops-personal-auth-token', "${pipelineContext.getBuildConfig().getGitHubCommitStateContext(config.stageName)}") {
          node(config.nodeLabel) {
            try {
              pipelineContext.getBuildSummary().setStageDetails(this, config.stageName, env.NODE_NAME, env.WORKSPACE)
              echo "###### Unstash scripts. ######"
              pipelineContext.getUtils().unstashScripts(this)

              sh "rm -rf ${config.stageDir}"

              def script = load(config.executionScript)
              script(pipelineContext, config)
              pipelineContext.getBuildSummary().markStageSuccessful(this, config.stageName)
            } catch (Exception e) {
              pipelineContext.getBuildSummary().markStageFailed(this, config.stageName)
              throw e
            }
          }
        }
      }
    }
  } else {
    echo "###### Changes for ${config.component} NOT detected, skipping ${config.stageName}. ######"
  }
}

return this
