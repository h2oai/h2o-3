def call(final pipelineContext) {

  def MODE_PR_CODE = 0
  def MODE_BENCHMARK_CODE = 1
  def MODE_HADOOP_CODE = 2
  def MODE_XGB_CODE = 3
  def MODE_COVERAGE_CODE = 4
  def MODE_SINGLE_TEST_CODE = 5
  def MODE_MASTER_CODE = 10
  def MODE_NIGHTLY_CODE = 20
  def MODES = [
    [name: 'MODE_PR', code: MODE_PR_CODE],
    [name: 'MODE_HADOOP', code: MODE_HADOOP_CODE],
    [name: 'MODE_XGB', code: MODE_XGB_CODE],
    [name: 'MODE_COVERAGE', code: MODE_COVERAGE_CODE],
    [name: 'MODE_SINGLE_TEST', code: MODE_SINGLE_TEST_CODE],
    [name: 'MODE_BENCHMARK', code: MODE_BENCHMARK_CODE],
    [name: 'MODE_MASTER', code: MODE_MASTER_CODE],
    [name: 'MODE_NIGHTLY', code: MODE_NIGHTLY_CODE]
  ]

  def modeCode = MODES.find{it['name'] == pipelineContext.getBuildConfig().getMode()}['code']

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
      stageName: 'Java 7 Smoke (no h2o-algos multinode)', target: 'test-junit-7-smoke-jenkins', javaVersion: 7, timeoutValue: 20,
      component: pipelineContext.getBuildConfig().COMPONENT_JAVA
    ],
    [
      stageName: 'Java 8 Smoke', target: 'test-junit-smoke-jenkins', javaVersion: 8, timeoutValue: 20,
      component: pipelineContext.getBuildConfig().COMPONENT_JAVA
    ],
     [
       stageName: 'Java 10 Smoke', target: 'test-junit-10-smoke-jenkins', javaVersion: 10, timeoutValue: 20,
       component: pipelineContext.getBuildConfig().COMPONENT_JAVA
     ],
    [
      stageName: 'Java 11 Smoke', target: 'test-junit-11-smoke-jenkins', javaVersion: 11, timeoutValue: 20,
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
      stageName: 'Py3.5 Single Node', target: 'test-pyunit-single-node', pythonVersion: '3.5',
      timeoutValue: 40, component: pipelineContext.getBuildConfig().COMPONENT_PY
    ],
    [
      stageName: 'Py2.7 Demos', target: 'test-py-demos', pythonVersion: '2.7',
      timeoutValue: 30, component: pipelineContext.getBuildConfig().COMPONENT_PY
    ],
    [
      stageName: 'Py2.7 Init Java 7', target: 'test-py-init', pythonVersion: '2.7', javaVersion: 7,
      timeoutValue: 10, hasJUnit: false, component: pipelineContext.getBuildConfig().COMPONENT_PY,
      image: "${pipelineContext.getBuildConfig().DOCKER_REGISTRY}/opsh2oai/h2o-3/dev-jdk-7:${pipelineContext.getBuildConfig().DEFAULT_IMAGE_VERSION_TAG}"
    ],
    [
      stageName: 'Py2.7 Init Java 8', target: 'test-py-init', pythonVersion: '2.7', javaVersion: 8,
      timeoutValue: 10, hasJUnit: false, component: pipelineContext.getBuildConfig().COMPONENT_PY,
      image: "${pipelineContext.getBuildConfig().DOCKER_REGISTRY}/opsh2oai/h2o-3/dev-jdk-8:${pipelineContext.getBuildConfig().DEFAULT_IMAGE_VERSION_TAG}"
    ],
     [
       stageName: 'Py2.7 Init Java 10', target: 'test-py-init', pythonVersion: '2.7', javaVersion: 10,
       timeoutValue: 10, hasJUnit: false, component: pipelineContext.getBuildConfig().COMPONENT_PY,
       image: "${pipelineContext.getBuildConfig().DOCKER_REGISTRY}/opsh2oai/h2o-3/dev-jdk-10:${pipelineContext.getBuildConfig().DEFAULT_IMAGE_VERSION_TAG}"
     ],
    [
      stageName: 'Py2.7 Init Java 11', target: 'test-py-init', pythonVersion: '2.7', javaVersion: 11,
      timeoutValue: 10, hasJUnit: false, component: pipelineContext.getBuildConfig().COMPONENT_PY,
      image: "${pipelineContext.getBuildConfig().DOCKER_REGISTRY}/opsh2oai/h2o-3/dev-jdk-11:${pipelineContext.getBuildConfig().DEFAULT_IMAGE_VERSION_TAG}"
    ],
    [
      stageName: 'Py3.5 Small', target: 'test-pyunit-small', pythonVersion: '3.5',
      timeoutValue: 90, component: pipelineContext.getBuildConfig().COMPONENT_PY
    ],
    [
      stageName: 'Py3.5 Small AutoML', target: 'test-pyunit-small-automl', pythonVersion: '3.5',
      timeoutValue: 90, component: pipelineContext.getBuildConfig().COMPONENT_PY
    ],
    [
      stageName: 'R3.4 Init Java 7', target: 'test-r-init', rVersion: '3.4.1', javaVersion: 7,
      timeoutValue: 10, hasJUnit: false, component: pipelineContext.getBuildConfig().COMPONENT_R,
      image: "${pipelineContext.getBuildConfig().DOCKER_REGISTRY}/opsh2oai/h2o-3/dev-r-3.4.1-jdk-7:${pipelineContext.getBuildConfig().DEFAULT_IMAGE_VERSION_TAG}"
    ],
    [
      stageName: 'R3.4 Init Java 8', target: 'test-r-init', rVersion: '3.4.1', javaVersion: 8,
      timeoutValue: 10, hasJUnit: false, component: pipelineContext.getBuildConfig().COMPONENT_R,
      image: "${pipelineContext.getBuildConfig().DOCKER_REGISTRY}/opsh2oai/h2o-3/dev-r-3.4.1-jdk-8:${pipelineContext.getBuildConfig().DEFAULT_IMAGE_VERSION_TAG}"
    ],
     [
       stageName: 'R3.4 Init Java 10', target: 'test-r-init', rVersion: '3.4.1', javaVersion: 10,
       timeoutValue: 10, hasJUnit: false, component: pipelineContext.getBuildConfig().COMPONENT_R,
       image: "${pipelineContext.getBuildConfig().DOCKER_REGISTRY}/opsh2oai/h2o-3/dev-r-3.4.1-jdk-10:${pipelineContext.getBuildConfig().DEFAULT_IMAGE_VERSION_TAG}"
     ],
    [
      stageName: 'R3.4 Init Java 11', target: 'test-r-init', rVersion: '3.4.1', javaVersion: 11,
      timeoutValue: 10, hasJUnit: false, component: pipelineContext.getBuildConfig().COMPONENT_R,
      image: "${pipelineContext.getBuildConfig().DOCKER_REGISTRY}/opsh2oai/h2o-3/dev-r-3.4.1-jdk-11:${pipelineContext.getBuildConfig().DEFAULT_IMAGE_VERSION_TAG}"
    ],
    [
      stageName: 'R3.4 Small', target: 'test-r-small', rVersion: '3.4.1',
      timeoutValue: 125, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'R3.4 Small Client Mode', target: 'test-r-small-client-mode', rVersion: '3.4.1',
      timeoutValue: 155, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'R3.4 Small AutoML', target: 'test-r-small-automl', rVersion: '3.4.1',
      timeoutValue: 125, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'R3.4 Small Client Mode AutoML', target: 'test-r-small-client-mode-automl', rVersion: '3.4.1',
      timeoutValue: 155, component: pipelineContext.getBuildConfig().COMPONENT_R
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
      timeoutValue: 80, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'R3.4 Demos Medium-large', target: 'test-r-demos-medium-large', rVersion: '3.4.1',
      timeoutValue: 140, component: pipelineContext.getBuildConfig().COMPONENT_R
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
      stageName: 'Java 8 JUnit', target: 'test-junit-jenkins', pythonVersion: '2.7', javaVersion: 8,
      timeoutValue: 180, component: pipelineContext.getBuildConfig().COMPONENT_JAVA, additionalTestPackages: [pipelineContext.getBuildConfig().COMPONENT_PY]
    ],
     [
       stageName: 'Java 10 JUnit', target: 'test-junit-10-jenkins', pythonVersion: '2.7', javaVersion: 10,
       timeoutValue: 180, component: pipelineContext.getBuildConfig().COMPONENT_JAVA, additionalTestPackages: [pipelineContext.getBuildConfig().COMPONENT_PY]
     ],
    [
      stageName: 'Java 11 JUnit', target: 'test-junit-11-jenkins', pythonVersion: '2.7', javaVersion: 11,
      timeoutValue: 180, component: pipelineContext.getBuildConfig().COMPONENT_JAVA, additionalTestPackages: [pipelineContext.getBuildConfig().COMPONENT_PY]
    ],
    [
      stageName: 'R3.4 Generate Docs', target: 'r-generate-docs-jenkins', archiveFiles: false,
      timeoutValue: 10, component: pipelineContext.getBuildConfig().COMPONENT_R, hasJUnit: false,
      archiveAdditionalFiles: ['r-generated-docs.zip'], installRPackage: false
    ],
    [
      stageName: 'MOJO Compatibility', target: 'test-mojo-compatibility',
      archiveFiles: false, timeoutValue: 20, pythonVersion: '3.6', hasJUnit: false,
      component: pipelineContext.getBuildConfig().COMPONENT_ANY, additionalTestPackages: [pipelineContext.getBuildConfig().COMPONENT_PY]
    ]
  ]

  def BENCHMARK_STAGES = [
    [
      stageName: 'GBM Benchmark', executionScript: 'h2o-3/scripts/jenkins/groovy/benchmarkStage.groovy',
      timeoutValue: 120, target: 'benchmark', component: pipelineContext.getBuildConfig().COMPONENT_ANY,
      additionalTestPackages: [pipelineContext.getBuildConfig().COMPONENT_R],
      customData: [algorithm: 'gbm'], makefilePath: pipelineContext.getBuildConfig().BENCHMARK_MAKEFILE_PATH,
      nodeLabel: pipelineContext.getBuildConfig().getBenchmarkNodeLabel()
    ],
    [
      stageName: 'GBM Benchmark Client', executionScript: 'h2o-3/scripts/jenkins/groovy/benchmarkStage.groovy',
      timeoutValue: 120, target: 'benchmark-gbm-client-mode', component: pipelineContext.getBuildConfig().COMPONENT_ANY,
      additionalTestPackages: [pipelineContext.getBuildConfig().COMPONENT_R],
      customData: [algorithm: 'gbm-client'], makefilePath: pipelineContext.getBuildConfig().BENCHMARK_MAKEFILE_PATH,
      nodeLabel: pipelineContext.getBuildConfig().getBenchmarkNodeLabel()
    ],
    [
      stageName: 'H2O XGB Benchmark', executionScript: 'h2o-3/scripts/jenkins/groovy/benchmarkStage.groovy',
      timeoutValue: 120, target: 'benchmark', component: pipelineContext.getBuildConfig().COMPONENT_ANY,
      additionalTestPackages: [pipelineContext.getBuildConfig().COMPONENT_R],
      customData: [algorithm: 'xgb'], makefilePath: pipelineContext.getBuildConfig().BENCHMARK_MAKEFILE_PATH,
      nodeLabel: pipelineContext.getBuildConfig().getBenchmarkNodeLabel(),
    ],
    [
      stageName: 'Vanilla XGB Benchmark', executionScript: 'h2o-3/scripts/jenkins/groovy/benchmarkStage.groovy',
      timeoutValue: 120, target: 'benchmark-xgb-vanilla', component: pipelineContext.getBuildConfig().COMPONENT_ANY,
      additionalTestPackages: [pipelineContext.getBuildConfig().COMPONENT_PY],
      customData: [algorithm: 'xgb-vanilla'], makefilePath: pipelineContext.getBuildConfig().BENCHMARK_MAKEFILE_PATH,
      nodeLabel: pipelineContext.getBuildConfig().getBenchmarkNodeLabel(),
    ],
    [
      stageName: 'DMLC XGB Benchmark', executionScript: 'h2o-3/scripts/jenkins/groovy/benchmarkStage.groovy',
      timeoutValue: 120, target: 'build-and-benchmark-latest-dmlc-r-xgboost', component: pipelineContext.getBuildConfig().COMPONENT_ANY,
      additionalTestPackages: [pipelineContext.getBuildConfig().COMPONENT_R],
      customData: [algorithm: 'xgb-dmlc'], makefilePath: pipelineContext.getBuildConfig().BENCHMARK_MAKEFILE_PATH,
      nodeLabel: pipelineContext.getBuildConfig().getBenchmarkNodeLabel(),
    ]
  ]

  // Stages executed in addition to PR_STAGES after merge to master.
  def MASTER_STAGES = [
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
      stageName: 'Py2.7 Single Node', target: 'test-pyunit-single-node', pythonVersion: '2.7',
      timeoutValue: 40, component: pipelineContext.getBuildConfig().COMPONENT_PY
    ],
    [
      stageName: 'Py3.6 Single Node', target: 'test-pyunit-single-node', pythonVersion: '3.6',
      timeoutValue: 40, component: pipelineContext.getBuildConfig().COMPONENT_PY
    ],
    [
      stageName: 'Py2.7 Small', target: 'test-pyunit-small', pythonVersion: '2.7',
      timeoutValue: 90, component: pipelineContext.getBuildConfig().COMPONENT_PY
    ],
    [
      stageName: 'Py3.6 Small', target: 'test-pyunit-small', pythonVersion: '3.6',
      timeoutValue: 90, component: pipelineContext.getBuildConfig().COMPONENT_PY
    ],
    [
      stageName: 'Py2.7 Small AutoML', target: 'test-pyunit-small-automl', pythonVersion: '2.7',
      timeoutValue: 90, component: pipelineContext.getBuildConfig().COMPONENT_PY
    ],
    [
      stageName: 'Py3.6 Small AutoML', target: 'test-pyunit-small-automl', pythonVersion: '3.6',
      timeoutValue: 90, component: pipelineContext.getBuildConfig().COMPONENT_PY
    ],
    [
      stageName: 'Py2.7 Medium-large', target: 'test-pyunit-medium-large', pythonVersion: '2.7',
      timeoutValue: 120, component: pipelineContext.getBuildConfig().COMPONENT_PY
    ],
    [
      stageName: 'Py3.5 Medium-large', target: 'test-pyunit-medium-large', pythonVersion: '3.5',
      timeoutValue: 120, component: pipelineContext.getBuildConfig().COMPONENT_PY
    ],
    [
      stageName: 'R3.3 Medium-large', target: 'test-r-medium-large', rVersion: '3.3.3',
      timeoutValue: 70, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'R3.3 Small', target: 'test-r-small', rVersion: '3.3.3',
      timeoutValue: 125, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'R3.3 Small Client Mode', target: 'test-r-small-client-mode', rVersion: '3.3.3',
      timeoutValue: 155, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'R3.3 Small AutoML', target: 'test-r-small-automl', rVersion: '3.3.3',
      timeoutValue: 125, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'R3.3 Small Client Mode AutoML', target: 'test-r-small-client-mode-automl', rVersion: '3.3.3',
      timeoutValue: 155, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'R3.3 CMD Check', target: 'test-r-cmd-check', rVersion: '3.3.3',
      timeoutValue: 15, hasJUnit: false, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'R3.3 CMD Check as CRAN', target: 'test-r-cmd-check-as-cran', rVersion: '3.3.3',
      timeoutValue: 10, hasJUnit: false, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [ // These run with reduced number of file descriptors for early detection of FD leaks
      stageName: 'XGBoost Stress tests', target: 'test-pyunit-xgboost-stress', pythonVersion: '3.5', timeoutValue: 20,
      component: pipelineContext.getBuildConfig().COMPONENT_PY, customDockerArgs: [ '--ulimit nofile=100:100' ]
    ]
  ]

  def HADOOP_STAGES = []
  for (distribution in pipelineContext.getBuildConfig().getSupportedHadoopDistributions()) {
    def stageTemplate = [
      target: 'test-hadoop-smoke', timeoutValue: 25, component: pipelineContext.getBuildConfig().COMPONENT_ANY,
      additionalTestPackages: [pipelineContext.getBuildConfig().COMPONENT_HADOOP, pipelineContext.getBuildConfig().COMPONENT_PY],
      customData: [
        distribution: distribution.name,
        version: distribution.version,
        ldapConfigPath: 'scripts/jenkins/ldap-conf.txt'
      ], pythonVersion: '2.7', nodeLabel: 'docker && micro',
      customDockerArgs: [ '--privileged' ],
      executionScript: 'h2o-3/scripts/jenkins/groovy/hadoopStage.groovy'
    ]
    def standaloneStage = evaluate(stageTemplate.inspect())
    standaloneStage.stageName = "${distribution.name.toUpperCase()} ${distribution.version} - STANDALONE"
    standaloneStage.customData.mode = 'STANDALONE'
    standaloneStage.image = pipelineContext.getBuildConfig().getSmokeHadoopImage(standaloneStage.customData.distribution, standaloneStage.customData.version, false)

    def onHadoopStage = evaluate(stageTemplate.inspect())
    onHadoopStage.stageName = "${distribution.name.toUpperCase()} ${distribution.version} - HADOOP"
    onHadoopStage.customData.mode = 'ON_HADOOP'
    onHadoopStage.image = pipelineContext.getBuildConfig().getSmokeHadoopImage(onHadoopStage.customData.distribution, onHadoopStage.customData.version, false)

    def withKRBStage = evaluate(stageTemplate.inspect())
    withKRBStage.stageName = "${distribution.name.toUpperCase()} ${distribution.version} - KRB"
    withKRBStage.customData.mode = 'WITH_KRB'
    withKRBStage.image = pipelineContext.getBuildConfig().getSmokeHadoopImage(withKRBStage.customData.distribution, withKRBStage.customData.version, true)

    HADOOP_STAGES += standaloneStage
    HADOOP_STAGES += onHadoopStage
    HADOOP_STAGES += withKRBStage
  }

  def XGB_STAGES = []
  for (String osName: pipelineContext.getBuildConfig().getSupportedXGBEnvironments().keySet()) {
    final def xgbEnvs = pipelineContext.getBuildConfig().getSupportedXGBEnvironments()[osName]
    xgbEnvs.each {xgbEnv ->
      final def stageDefinition = [
        stageName: "XGB on ${xgbEnv.name}", target: "test-xgb-smoke-${xgbEnv.targetName}-jenkins",
        timeoutValue: 15, component: pipelineContext.getBuildConfig().COMPONENT_ANY,
        additionalTestPackages: [pipelineContext.getBuildConfig().COMPONENT_JAVA], pythonVersion: '3.5',
        image: pipelineContext.getBuildConfig().getXGBImageForEnvironment(osName, xgbEnv),
        nodeLabel: pipelineContext.getBuildConfig().getXGBNodeLabelForEnvironment(xgbEnv)
      ]
      if (xgbEnv.targetName == pipelineContext.getBuildConfig().XGB_TARGET_GPU) {
        stageDefinition['executionScript'] = 'h2o-3/scripts/jenkins/groovy/xgbGPUStage.groovy'
      }
      XGB_STAGES += stageDefinition
    }
  }

  def COVERAGE_STAGES = [
    [
      stageName: 'h2o-algos Coverage', target: 'coverage-junit-algos', pythonVersion: '2.7', timeoutValue: 5 * 60,
      executionScript: 'h2o-3/scripts/jenkins/groovy/coverageStage.groovy',
      component: pipelineContext.getBuildConfig().COMPONENT_JAVA, archiveAdditionalFiles: ['build/reports/jacoco/*.exec'],
      additionalTestPackages: [pipelineContext.getBuildConfig().COMPONENT_PY], nodeLabel: "${pipelineContext.getBuildConfig().getDefaultNodeLabel()} && !micro"
    ]
  ]

  def SINGLE_TEST_STAGES = []
  if (modeCode == MODE_SINGLE_TEST_CODE) {
    if (params.testPath == null || params.testPath == '') {
      error 'Parameter testPath must be set.'
    }

    env.SINGLE_TEST_PATH = params.testPath.trim()
    env.SINGLE_TEST_XMX = params.singleTestXmx
    env.SINGLE_TEST_NUM_NODES = params.singleTestNumNodes

    def target
    def additionalTestPackage
    switch (params.testComponent) {
      case 'Python':
        target = 'test-py-single-test'
        additionalTestPackage = pipelineContext.getBuildConfig().COMPONENT_PY
        break
      case 'R':
        target = 'test-r-single-test'
        additionalTestPackage = pipelineContext.getBuildConfig().COMPONENT_R
        break
      default:
        error "Test Component ${params.testComponent} not supported"
    }
    def numRunsNum = -1
    try {
      numRunsNum = Integer.parseInt(params.singleTestNumRuns)
    } catch (NumberFormatException e) {
      error "singleTestNumRuns must be a valid number"
    }
    numRunsNum.times {
      SINGLE_TEST_STAGES += [
        stageName: "Test ${params.testPath.split('/').last()} #${(it + 1)}", target: target, timeoutValue: 25,
        component: pipelineContext.getBuildConfig().COMPONENT_ANY, additionalTestPackages: [additionalTestPackage],
        pythonVersion: params.singleTestPyVersion, rVersion: params.singleTestRVersion
      ]
    }
  }

  if (modeCode == MODE_BENCHMARK_CODE) {
    executeInParallel(BENCHMARK_STAGES, pipelineContext)
  } else if (modeCode == MODE_HADOOP_CODE) {
    executeInParallel(HADOOP_STAGES, pipelineContext)
  } else if (modeCode == MODE_XGB_CODE) {
    executeInParallel(XGB_STAGES, pipelineContext)
  } else if (modeCode == MODE_COVERAGE_CODE) {
    executeInParallel(COVERAGE_STAGES, pipelineContext)
  } else if (modeCode == MODE_SINGLE_TEST_CODE) {
    executeInParallel(SINGLE_TEST_STAGES, pipelineContext)
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
          installRPackage = c['installRPackage']
          javaVersion = c['javaVersion']
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
          archiveFiles = c['archiveFiles']
          activatePythonEnv = c['activatePythonEnv']
	      customDockerArgs = c['customDockerArgs']
        }
      }
    ]
  })
}

private void invokeStage(final pipelineContext, final body) {

  final String DEFAULT_JAVA = '8'
  final String DEFAULT_PYTHON = '3.5'
  final String DEFAULT_R = '3.4.1'
  final int DEFAULT_TIMEOUT = 60
  final String DEFAULT_EXECUTION_SCRIPT = 'h2o-3/scripts/jenkins/groovy/defaultStage.groovy'
  final int HEALTH_CHECK_RETRIES = 5

  def config = [:]

  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  config.stageDir = pipelineContext.getUtils().stageNameToDirName(config.stageName)

  config.pythonVersion = config.pythonVersion ?: DEFAULT_PYTHON
  config.rVersion = config.rVersion ?: DEFAULT_R
  config.javaVersion = config.javaVersion ?: DEFAULT_JAVA
  config.timeoutValue = config.timeoutValue ?: DEFAULT_TIMEOUT
  config.customDockerArgs = config.customDockerArgs ?: []
  if (config.hasJUnit == null) {
    config.hasJUnit = true
  }
  config.additionalTestPackages = config.additionalTestPackages ?: []
  config.nodeLabel = config.nodeLabel ?: pipelineContext.getBuildConfig().getDefaultNodeLabel()
  config.executionScript = config.executionScript ?: DEFAULT_EXECUTION_SCRIPT
  config.makefilePath = config.makefilePath ?: pipelineContext.getBuildConfig().MAKEFILE_PATH
  config.archiveAdditionalFiles = config.archiveAdditionalFiles ?: []
  config.excludeAdditionalFiles = config.excludeAdditionalFiles ?: []
  if (config.archiveFiles == null) {
    config.archiveFiles = true
  }

  if (config.installRPackage == null) {
      config.installRPackage = true
  }

  if (config.activatePythonEnv == null) {
    config.activatePythonEnv = config.component == pipelineContext.getBuildConfig().COMPONENT_PY ||
            config.component == pipelineContext.getBuildConfig().COMPONENT_JS ||
            config.additionalTestPackages.contains(pipelineContext.getBuildConfig().COMPONENT_PY)
  }
  config.image = config.image ?: pipelineContext.getBuildConfig().getStageImage(config)

  if (pipelineContext.getBuildConfig().componentChanged(config.component)) {
    def stageClosure = {
      pipelineContext.getBuildSummary().addStageSummary(this, config.stageName, config.stageDir)
      stage(config.stageName) {
        if (params.executeFailedOnly && pipelineContext.getUtils().wasStageSuccessful(this, config.stageName)) {
          echo "###### Stage was successful in previous build ######"
          pipelineContext.getBuildSummary().setStageDetails(this, config.stageName, 'Skipped', 'N/A')
          pipelineContext.getBuildSummary().markStageSuccessful(this, config.stageName)
        } else {
          boolean healthCheckPassed = false
          int attempt = 0
          String nodeLabel = config.nodeLabel
          try {
            while (!healthCheckPassed) {
              attempt += 1
              if (attempt > HEALTH_CHECK_RETRIES) {
                error "Too many attempts to pass initial health check"
              }
              nodeLabel = pipelineContext.getHealthChecker().getHealthyNodesLabel(config.nodeLabel)
              echo "######### NodeLabel: ${nodeLabel} #########"
              node(nodeLabel) {
                echo "###### Unstash scripts. ######"
                pipelineContext.getUtils().unstashScripts(this)

                healthCheckPassed = pipelineContext.getHealthChecker().checkHealth(this, env.NODE_NAME, config.image, pipelineContext.getBuildConfig().DOCKER_REGISTRY, pipelineContext.getBuildConfig())
                if (healthCheckPassed) {
                  pipelineContext.getBuildSummary().setStageDetails(this, config.stageName, env.NODE_NAME, env.WORKSPACE)

                  sh "rm -rf ${config.stageDir}"

                  def script = load(config.executionScript)
                  script(pipelineContext, config)
                  pipelineContext.getBuildSummary().markStageSuccessful(this, config.stageName)
                }
              }
            }
          } catch (Exception e) {
            pipelineContext.getBuildSummary().markStageFailed(this, config.stageName)
            throw e
          }
        }
      }
    }
    if (env.BUILDING_FORK) {
      withCustomCommitStates(scm, pipelineContext.getBuildConfig().H2O_OPS_TOKEN, config.stageName) {
        stageClosure()
      }
    } else {
      stageClosure()
    }
  } else {
    echo "###### Changes for ${config.component} NOT detected, skipping ${config.stageName}. ######"
  }
}

return this
