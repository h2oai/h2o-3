def call(buildConfig) {

  def MODE_PR_TESTING_CODE = -1
  def MODE_PR_CODE = 0
  def MODE_BENCHMARK_CODE = 1
  def MODE_MASTER_CODE = 2
  def MODE_NIGHTLY_CODE = 3
  def MODES = [
    [name: 'MODE_PR_TESTING', code: MODE_PR_TESTING_CODE],
    [name: 'MODE_PR', code: MODE_PR_CODE],
    [name: 'MODE_BENCHMARK', code: MODE_BENCHMARK_CODE],
    [name: 'MODE_MASTER', code: MODE_MASTER_CODE],
    [name: 'MODE_NIGHTLY', code: MODE_NIGHTLY_CODE]
  ]

  def BENCHMARK_MAKEFILE_PATH = 'ml-benchmark/jenkins/Makefile.jenkins'

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
      timeoutValue: 20, lang: buildConfig.LANG_JS
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
      timeoutValue: 30, lang: buildConfig.LANG_PY
    ],
    [
      stageName: 'Py2.7 Init', target: 'test-py-init', pythonVersion: '2.7',
      timeoutValue: 5, hasJUnit: false, lang: buildConfig.LANG_PY
    ],
    [
      stageName: 'Py2.7 Small', target: 'test-pyunit-small', pythonVersion: '2.7',
      timeoutValue: 90, lang: buildConfig.LANG_PY
    ],
    [
      stageName: 'Py3.5 Small', target: 'test-pyunit-small', pythonVersion: '3.5',
      timeoutValue: 90, lang: buildConfig.LANG_PY
    ],
    [
      stageName: 'Py3.6 Small', target: 'test-pyunit-small', pythonVersion: '3.6',
      timeoutValue: 90, lang: buildConfig.LANG_PY
    ],
    [
      stageName: 'R3.4 Init', target: 'test-r-init', rVersion: '3.4.1',
      timeoutValue: 5, hasJUnit: false, lang: buildConfig.LANG_R
    ],
    [
      stageName: 'R3.4 Small', target: 'test-r-small', rVersion: '3.4.1',
      timeoutValue: 110, lang: buildConfig.LANG_R
    ],
    [
      stageName: 'R3.4 Small Client Mode', target: 'test-r-small-client-mode', rVersion: '3.4.1',
      timeoutValue: 140, lang: buildConfig.LANG_R
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
      timeoutValue: 75, lang: buildConfig.LANG_JS
    ],
    [
      stageName: 'Py3.6 Medium-large', target: 'test-pyunit-medium-large', pythonVersion: '3.5',
      timeoutValue: 120, lang: buildConfig.LANG_PY
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
      stageName: 'Java 8 JUnit', target: 'test-junit-jenkins', pythonVersion: '2.7',
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

  def BENCHMARK_STAGES = [
    [
      stageName: 'GBM Benchmark', executionScript: 'h2o-3/scripts/jenkins/groovy/benchmarkStage.groovy',
      timeoutValue: 120, target: 'benchmark', lang: buildConfig.LANG_NONE,
      additionalTestPackages: [buildConfig.LANG_R], image: buildConfig.BENCHMARK_IMAGE,
      nodeLabel: buildConfig.getBenchmarkNodeLabel(), model: 'gbm', makefilePath: BENCHMARK_MAKEFILE_PATH
    ]
  ]

  // Stages executed in addition to PR_STAGES after merge to master.
  def MASTER_STAGES = [
    [
      stageName: 'Py2.7 Medium-large', target: 'test-pyunit-medium-large', pythonVersion: '2.7',
      timeoutValue: 120, lang: buildConfig.LANG_PY
    ],
    [
      stageName: 'Py3.5 Medium-large', target: 'test-pyunit-medium-large', pythonVersion: '3.5',
      timeoutValue: 120, lang: buildConfig.LANG_PY
    ],
    [
      stageName: 'R3.4 Datatable', target: 'test-r-datatable', rVersion: '3.4.1',
      timeoutValue: 40, lang: buildConfig.LANG_R
    ],
    [
      stageName: 'PhantomJS Small', target: 'test-phantom-js-small',
      timeoutValue: 75, lang: buildConfig.LANG_JS
    ],
    [
      stageName: 'PhantomJS Medium', target: 'test-phantom-js-medium',
      timeoutValue: 75, lang: buildConfig.LANG_JS
    ]
  ]
  MASTER_STAGES += BENCHMARK_STAGES

  // Stages executed in addition to MASTER_STAGES, used for nightly builds.
  def NIGHTLY_STAGES = [
    [
      stageName: 'R3.3 Medium-large', target: 'test-r-medium-large', rVersion: '3.3.3',
      timeoutValue: 70, lang: buildConfig.LANG_R
    ],
    [
      stageName: 'R3.3 Small', target: 'test-r-small', rVersion: '3.3.3',
      timeoutValue: 110, lang: buildConfig.LANG_R
    ],
    [
      stageName: 'R3.3 Small Client Mode', target: 'test-r-small-client-mode', rVersion: '3.3.3',
      timeoutValue: 140, lang: buildConfig.LANG_R
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

  def modeCode = MODES.find{it['name'] == buildConfig.getMode()}['code']
  if (modeCode == MODE_BENCHMARK_CODE) {
    executeInParallel(BENCHMARK_STAGES, buildConfig)
  } else {
    executeInParallel(SMOKE_STAGES, buildConfig)
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
}

def executeInParallel(jobs, buildConfig) {
  parallel(jobs.collectEntries { c ->
    [
      c['stageName'], {
        invokeStage(buildConfig) {
          stageName = c['stageName']
          target = c['target']
          pythonVersion = c['pythonVersion']
          rVersion = c['rVersion']
          timeoutValue = c['timeoutValue']
          hasJUnit = c['hasJUnit']
          lang = c['lang']
          additionalTestPackages = c['additionalTestPackages']
          nodeLabel = c['nodeLabel']
          executionScript = c['executionScript']
          image = c['image']
          model = c['model']
          makefilePath = c['makefilePath']
        }
      }
    ]
  })
}

def invokeStage(buildConfig, body) {

  def DEFAULT_PYTHON = '3.5'
  def DEFAULT_R = '3.4.1'
  def DEFAULT_TIMEOUT = 60
  def DEFAULT_EXECUTION_SCRIPT = 'h2o-3/scripts/jenkins/groovy/defaultStage.groovy'
  def DEFAULT_MAKEFILE_PATH = 'docker/Makefile.jenkins'

  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  if (config.pythonVersion == null) {
    config.pythonVersion = DEFAULT_PYTHON
  }
  if (config.rVersion == null) {
    config.rVersion = DEFAULT_R
  }
  if (config.timeoutValue == null) {
    config.timeoutValue = DEFAULT_TIMEOUT
  }
  if (config.hasJUnit == null) {
    config.hasJUnit = true
  }
  if (config.additionalTestPackages == null) {
    config.additionalTestPackages = []
  }
  if (config.nodeLabel == null) {
    config.nodeLabel = buildConfig.getDefaultNodeLabel()
  }
  if (config.executionScript == null) {
    config.executionScript = DEFAULT_EXECUTION_SCRIPT
  }
  if (config.image == null) {
    config.image = buildConfig.DEFAULT_IMAGE
  }
  if (config.makefilePath == null) {
    config.makefilePath = DEFAULT_MAKEFILE_PATH
  }

  buildConfig.addStageSummary(this, config.stageName)
  withCustomCommitStates(scm, 'h2o-ops-personal-auth-token', "${buildConfig.getGitHubCommitStateContext(config.stageName)}") {
    try {
      node(config.nodeLabel) {
        buildConfig.setStageDetails(this, config.stageName, env.NODE_NAME, env.WORKSPACE)
        echo "###### Unstash scripts. ######"
        unstash name: buildConfig.PIPELINE_SCRIPTS_STASH_NAME

        if (config.stageDir == null) {
          def stageNameToDirName = load('h2o-3/scripts/jenkins/groovy/stageNameToDirName.groovy')
          config.stageDir = stageNameToDirName(config.stageName)
        }
        sh "rm -rf ${config.stageDir}"

        def script = load(config.executionScript)
        script(buildConfig, config)
      }
      buildConfig.markStageSuccessful(this, config.stageName)
      echo "Build Summary: ${buildConfig.getBuildSummary().toString()}"
    } catch (Exception e) {
      buildConfig.markStageFailed(this, config.stageName)
      throw e
    }
  }
}

return this
