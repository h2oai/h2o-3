def call(final pipelineContext) {

  def MODE_PR_CODE = 0
  def MODE_BENCHMARK_CODE = 1
  def MODE_HADOOP_CODE = 2
  def MODE_XGB_CODE = 3
  def MODE_COVERAGE_CODE = 4
  def MODE_SINGLE_TEST_CODE = 5
  def MODE_KERBEROS_CODE = 6
  def MODE_HADOOP_MULTINODE_CODE = 7
  def MODE_MASTER_CODE = 10
  def MODE_NIGHTLY_CODE = 20
  def MODES = [
    [name: 'MODE_PR', code: MODE_PR_CODE],
    [name: 'MODE_HADOOP', code: MODE_HADOOP_CODE],
    [name: 'MODE_KERBEROS', code: MODE_KERBEROS_CODE],
    [name: 'MODE_HADOOP_MULTINODE', code: MODE_HADOOP_MULTINODE_CODE],
    [name: 'MODE_XGB', code: MODE_XGB_CODE],
    [name: 'MODE_COVERAGE', code: MODE_COVERAGE_CODE],
    [name: 'MODE_SINGLE_TEST', code: MODE_SINGLE_TEST_CODE],
    [name: 'MODE_BENCHMARK', code: MODE_BENCHMARK_CODE],
    [name: 'MODE_MASTER', code: MODE_MASTER_CODE],
    [name: 'MODE_NIGHTLY', code: MODE_NIGHTLY_CODE]
  ]

  def modeCode = MODES.find{it['name'] == pipelineContext.getBuildConfig().getMode()}['code']

  // Job will execute PR_STAGES only if these are green.
  // for Python, smoke only oldest and latest supported versions
  def SMOKE_STAGES = [
    [
      stageName: 'Py2.7 Smoke', target: 'test-py-smoke', pythonVersion: '2.7',timeoutValue: 8,
      component: pipelineContext.getBuildConfig().COMPONENT_PY
    ],
    [
      stageName: 'Py3.7 Smoke', target: 'test-py-smoke', pythonVersion: '3.7',timeoutValue: 8,
      component: pipelineContext.getBuildConfig().COMPONENT_PY
    ],
    [
      stageName: 'R3.5 Smoke', target: 'test-r-smoke', rVersion: '3.5.3',timeoutValue: 8,
      component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'R4.0 Smoke', target: 'test-r-smoke', rVersion: '4.0.2',timeoutValue: 8,
      component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'Flow Headless Smoke', target: 'test-flow-headless-smoke',timeoutValue: 20,
      component: pipelineContext.getBuildConfig().COMPONENT_JS
    ],
    [
      stageName: 'Java 8 Smoke', target: 'test-junit-smoke-jenkins', javaVersion: 8, timeoutValue: 20,
      component: pipelineContext.getBuildConfig().COMPONENT_JAVA
    ]
  ]

  // Stages executed after each push to PR branch.
  // for Python, mainly test with latest supported version
  def PR_STAGES = [
    [
      stageName: 'Py2.7 Booklets', target: 'test-py-booklets', pythonVersion: '2.7',
      timeoutValue: 40, component: pipelineContext.getBuildConfig().COMPONENT_PY
    ],
    [
      stageName: 'Py2.7 Init Java 8', target: 'test-py-init', pythonVersion: '2.7', javaVersion: 8,
      timeoutValue: 10, hasJUnit: false, component: pipelineContext.getBuildConfig().COMPONENT_PY,
      image: "${pipelineContext.getBuildConfig().DOCKER_REGISTRY}/opsh2oai/h2o-3/dev-jdk-8:${pipelineContext.getBuildConfig().DEFAULT_IMAGE_VERSION_TAG}"
    ],
    [
      stageName: 'Py3.7 Single Node', target: 'test-pyunit-single-node', pythonVersion: '3.7',
      timeoutValue: 40, component: pipelineContext.getBuildConfig().COMPONENT_PY
    ],
    [
      stageName: 'Py3.7 Small', target: 'test-pyunit-small', pythonVersion: '3.7',
      timeoutValue: 90, component: pipelineContext.getBuildConfig().COMPONENT_PY
    ],
    [
      stageName: 'Py3.7 AutoML', target: 'test-pyunit-automl', pythonVersion: '3.7',
      timeoutValue: 90, component: pipelineContext.getBuildConfig().COMPONENT_PY
    ],
    [
      stageName: 'R3.5 Init Java 8', target: 'test-r-init', rVersion: '3.5.3', javaVersion: 8,
      timeoutValue: 10, hasJUnit: false, component: pipelineContext.getBuildConfig().COMPONENT_R,
      image: "${pipelineContext.getBuildConfig().DOCKER_REGISTRY}/opsh2oai/h2o-3/dev-r-3.5.3-jdk-8:${pipelineContext.getBuildConfig().DEFAULT_IMAGE_VERSION_TAG}"
    ],
    [
      stageName: 'R3.5 Small', target: 'test-r-small', rVersion: '3.5.3',
      timeoutValue: 125, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'R3.5 Small Client Mode', target: 'test-r-small-client-mode', rVersion: '3.5.3',
      timeoutValue: 155, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'R3.5 Small Client Mode Disconnect Attack', target: 'test-r-small-client-mode-attack', rVersion: '3.5.3',
      timeoutValue: 155, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'R3.5 AutoML', target: 'test-r-automl', rVersion: '3.5.3',
      timeoutValue: 125, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'R3.5 Client Mode AutoML', target: 'test-r-client-mode-automl', rVersion: '3.5.3',
      timeoutValue: 155, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'R3.5 CMD Check', target: 'test-r-cmd-check', rVersion: '3.5.3',
      timeoutValue: 30, hasJUnit: false, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'R3.5 CMD Check as CRAN', target: 'test-r-cmd-check-as-cran', rVersion: '3.5.3',
      timeoutValue: 20, hasJUnit: false, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'R4.0 Small', target: 'test-r-small', rVersion: '4.0.2',
      timeoutValue: 125, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'R4.0 CMD Check as CRAN', target: 'test-r-cmd-check-as-cran', rVersion: '4.0.2',
      timeoutValue: 20, hasJUnit: false, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'R3.5 Booklets', target: 'test-r-booklets', rVersion: '3.5.3',
      timeoutValue: 50, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'R3.5 Demos Small', target: 'test-r-demos-small', rVersion: '3.5.3',
      timeoutValue: 15, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'Flow Headless', target: 'test-flow-headless',
      timeoutValue: 75, component: pipelineContext.getBuildConfig().COMPONENT_JS
    ],
    [
      stageName: 'Py3.7 Medium-large', target: 'test-pyunit-medium-large', pythonVersion: '3.7',
      timeoutValue: 150, component: pipelineContext.getBuildConfig().COMPONENT_PY
    ],
    [
      stageName: 'R3.5 Medium-large', target: 'test-r-medium-large', rVersion: '3.5.3',
      timeoutValue: 80, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'R3.5 Demos Medium-large', target: 'test-r-demos-medium-large', rVersion: '3.5.3',
      timeoutValue: 140, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'INFO Check', target: 'test-info',
      timeoutValue: 10, component: pipelineContext.getBuildConfig().COMPONENT_ANY, additionalTestPackages: [pipelineContext.getBuildConfig().COMPONENT_R]
    ],
    [
      stageName: 'Py2.7 Demos', target: 'test-py-demos', pythonVersion: '2.7',
      timeoutValue: 30, component: pipelineContext.getBuildConfig().COMPONENT_PY
    ],
    [
      stageName: 'Py3.7 Test Demos', target: 'test-pyunit-demos', pythonVersion: '3.7',
      timeoutValue: 10, component: pipelineContext.getBuildConfig().COMPONENT_PY
    ],
    [
      stageName: 'Java 8 JUnit', target: 'test-junit-jenkins', pythonVersion: '2.7', javaVersion: 8,
      timeoutValue: 180, component: pipelineContext.getBuildConfig().COMPONENT_JAVA, additionalTestPackages: [pipelineContext.getBuildConfig().COMPONENT_PY]
    ],
    [
      stageName: 'Java 8 AutoML JUnit', target: 'test-junit-automl-jenkins', pythonVersion: '2.7', javaVersion: 8,
      timeoutValue: 120, component: pipelineContext.getBuildConfig().COMPONENT_JAVA, additionalTestPackages: [pipelineContext.getBuildConfig().COMPONENT_PY]
    ],
    [
      stageName: 'Java 8 XGBoost Multinode JUnit', target: 'test-junit-xgb-multi-jenkins', pythonVersion: '2.7', javaVersion: 8,
      timeoutValue: 120, component: pipelineContext.getBuildConfig().COMPONENT_JAVA, additionalTestPackages: [pipelineContext.getBuildConfig().COMPONENT_PY]
    ],
    [
      stageName: 'R3.5 Generate Docs', target: 'r-generate-docs-jenkins', archiveFiles: false,
      timeoutValue: 10, component: pipelineContext.getBuildConfig().COMPONENT_R, hasJUnit: false,
      archiveAdditionalFiles: ['r-generated-docs.zip'], installRPackage: false
    ],
    [
      stageName: 'MOJO Compatibility (Java 7)', target: 'test-mojo-compatibility',
      archiveFiles: false, timeoutValue: 20, hasJUnit: false, pythonVersion: '3.6', javaVersion: 7,
      component: pipelineContext.getBuildConfig().COMPONENT_JAVA, // only run when Java changes (R/Py cannot affect mojo)
      imageSpecifier: "mojocompat",
      additionalTestPackages: [pipelineContext.getBuildConfig().COMPONENT_PY]
    ],
  ]

  def BENCHMARK_STAGES = [
    [
      stageName: 'GBM Benchmark', executionScript: 'h2o-3/scripts/jenkins/groovy/benchmarkStage.groovy',
      timeoutValue: 120, target: 'benchmark', component: pipelineContext.getBuildConfig().COMPONENT_ANY,
      additionalTestPackages: [pipelineContext.getBuildConfig().COMPONENT_R],
      customData: [algorithm: 'gbm'], makefilePath: pipelineContext.getBuildConfig().BENCHMARK_MAKEFILE_PATH,
      nodeLabel: pipelineContext.getBuildConfig().getBenchmarkNodeLabel(),
      healthCheckSuppressed: true
    ],
    [
      stageName: 'GLM Benchmark', executionScript: 'h2o-3/scripts/jenkins/groovy/benchmarkStage.groovy',
      timeoutValue: 120, target: 'benchmark', component: pipelineContext.getBuildConfig().COMPONENT_ANY,
      additionalTestPackages: [pipelineContext.getBuildConfig().COMPONENT_R],
      customData: [algorithm: 'glm'], makefilePath: pipelineContext.getBuildConfig().BENCHMARK_MAKEFILE_PATH,
      nodeLabel: pipelineContext.getBuildConfig().getBenchmarkNodeLabel(),
      healthCheckSuppressed: true
    ],
    [ 
      stageName: 'GAM Benchmark', executionScript: 'h2o-3/scripts/jenkins/groovy/benchmarkStage.groovy',
      timeoutValue: 120, target: 'benchmark', component: pipelineContext.getBuildConfig().COMPONENT_ANY,
      additionalTestPackages: [pipelineContext.getBuildConfig().COMPONENT_R],
      customData: [algorithm: 'gam'], makefilePath: pipelineContext.getBuildConfig().BENCHMARK_MAKEFILE_PATH,
      nodeLabel: pipelineContext.getBuildConfig().getBenchmarkNodeLabel(),
      healthCheckSuppressed: true
    ],
    [
      stageName: 'GBM Benchmark Client', executionScript: 'h2o-3/scripts/jenkins/groovy/benchmarkStage.groovy',
      timeoutValue: 120, target: 'benchmark-gbm-client-mode', component: pipelineContext.getBuildConfig().COMPONENT_ANY,
      additionalTestPackages: [pipelineContext.getBuildConfig().COMPONENT_R],
      customData: [algorithm: 'gbm-client'], makefilePath: pipelineContext.getBuildConfig().BENCHMARK_MAKEFILE_PATH,
      nodeLabel: pipelineContext.getBuildConfig().getBenchmarkNodeLabel(),
      healthCheckSuppressed: true
    ],
    [
      stageName: 'H2O XGB Benchmark', executionScript: 'h2o-3/scripts/jenkins/groovy/benchmarkStage.groovy',
      timeoutValue: 120, target: 'benchmark', component: pipelineContext.getBuildConfig().COMPONENT_ANY,
      additionalTestPackages: [pipelineContext.getBuildConfig().COMPONENT_R],
      customData: [algorithm: 'xgb'], makefilePath: pipelineContext.getBuildConfig().BENCHMARK_MAKEFILE_PATH,
      nodeLabel: pipelineContext.getBuildConfig().getBenchmarkNodeLabel(),
      healthCheckSuppressed: true
    ],
    [
      stageName: 'H2O XGB GPU Benchmark', executionScript: 'h2o-3/scripts/jenkins/groovy/benchmarkStage.groovy',
      customDockerArgs: ['--runtime=nvidia', '--pid=host'],
      timeoutValue: 120, target: 'benchmark-xgb-gpu', component: pipelineContext.getBuildConfig().COMPONENT_ANY,
      dockerImageSuffix: "gpu",
      additionalTestPackages: [pipelineContext.getBuildConfig().COMPONENT_R],
      customData: [algorithm: 'xgb'], makefilePath: pipelineContext.getBuildConfig().BENCHMARK_MAKEFILE_PATH,
      nodeLabel: pipelineContext.getBuildConfig().getGPUBenchmarkNodeLabel(),
      healthCheckSuppressed: true
    ],
    [
      stageName: 'Vanilla XGB Benchmark', executionScript: 'h2o-3/scripts/jenkins/groovy/benchmarkStage.groovy',
      timeoutValue: 120, target: 'benchmark-xgb-vanilla', component: pipelineContext.getBuildConfig().COMPONENT_ANY,
      additionalTestPackages: [pipelineContext.getBuildConfig().COMPONENT_PY],
      customData: [algorithm: 'xgb-vanilla'], makefilePath: pipelineContext.getBuildConfig().BENCHMARK_MAKEFILE_PATH,
      nodeLabel: pipelineContext.getBuildConfig().getBenchmarkNodeLabel(),
      healthCheckSuppressed: true
    ],
    [
      stageName: 'DMLC XGB Benchmark', executionScript: 'h2o-3/scripts/jenkins/groovy/benchmarkStage.groovy',
      timeoutValue: 120, target: 'benchmark-dmlc-r-xgboost', component: pipelineContext.getBuildConfig().COMPONENT_ANY,
      additionalTestPackages: [pipelineContext.getBuildConfig().COMPONENT_R],
      customData: [algorithm: 'xgb-dmlc'], makefilePath: pipelineContext.getBuildConfig().BENCHMARK_MAKEFILE_PATH,
      nodeLabel: pipelineContext.getBuildConfig().getBenchmarkNodeLabel(),
      healthCheckSuppressed: true
    ],
    [ 
      stageName: 'MERGE Benchmark', executionScript: 'h2o-3/scripts/jenkins/groovy/benchmarkStage.groovy',
      timeoutValue: 120, target: 'benchmark', component: pipelineContext.getBuildConfig().COMPONENT_ANY,
      additionalTestPackages: [pipelineContext.getBuildConfig().COMPONENT_R],
      customData: [algorithm: 'merge'], makefilePath: pipelineContext.getBuildConfig().BENCHMARK_MAKEFILE_PATH,
      nodeLabel: pipelineContext.getBuildConfig().getBenchmarkNodeLabel(),
      healthCheckSuppressed: true
    ],
    [
      stageName: 'SORT Benchmark', executionScript: 'h2o-3/scripts/jenkins/groovy/benchmarkStage.groovy',
      timeoutValue: 120, target: 'benchmark', component: pipelineContext.getBuildConfig().COMPONENT_ANY,
      additionalTestPackages: [pipelineContext.getBuildConfig().COMPONENT_R],
      customData: [algorithm: 'sort'], makefilePath: pipelineContext.getBuildConfig().BENCHMARK_MAKEFILE_PATH,
      nodeLabel: pipelineContext.getBuildConfig().getBenchmarkNodeLabel(),
      healthCheckSuppressed: true
    ],
    [
      stageName: 'Rulefit Benchmark', executionScript: 'h2o-3/scripts/jenkins/groovy/benchmarkStage.groovy',
      timeoutValue: 120, target: 'benchmark', component: pipelineContext.getBuildConfig().COMPONENT_ANY,
      additionalTestPackages: [pipelineContext.getBuildConfig().COMPONENT_R],
      customData: [algorithm: 'rulefit'], makefilePath: pipelineContext.getBuildConfig().BENCHMARK_MAKEFILE_PATH,
      nodeLabel: pipelineContext.getBuildConfig().getBenchmarkNodeLabel(),
      healthCheckSuppressed: true
    ],
  ]

  // Stages executed in addition to PR_STAGES after merge to master.
  def MASTER_STAGES = [
    [
      stageName: 'R3.5 Datatable', target: 'test-r-datatable', rVersion: '3.5.3',
      timeoutValue: 40, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'Flow Headless Small', target: 'test-flow-headless-small',
      timeoutValue: 75, component: pipelineContext.getBuildConfig().COMPONENT_JS
    ],
    [
      stageName: 'Flow Headless Medium', target: 'test-flow-headless-medium',
      timeoutValue: 75, component: pipelineContext.getBuildConfig().COMPONENT_JS
    ]
  ]

  // Stages executed in addition to MASTER_STAGES, used for nightly builds.
  // for Python, rerun all tests with 2.7 and oldest 3.x supported version.
  def NIGHTLY_STAGES = [
    [
      stageName: 'Java 10 Smoke', target: 'test-junit-10-smoke-jenkins', javaVersion: 10, timeoutValue: 20,
      component: pipelineContext.getBuildConfig().COMPONENT_JAVA
    ],
    [
      stageName: 'Java 11 Smoke', target: 'test-junit-11-smoke-jenkins', javaVersion: 11, timeoutValue: 20,
      component: pipelineContext.getBuildConfig().COMPONENT_JAVA
    ],
    [
      stageName: 'Java 12 Smoke', target: 'test-junit-12-smoke-jenkins', javaVersion: 12, timeoutValue: 20,
      component: pipelineContext.getBuildConfig().COMPONENT_JAVA
    ],
    [
      stageName: 'Java 13 Smoke', target: 'test-junit-13-smoke-jenkins', javaVersion: 13, timeoutValue: 20,
      component: pipelineContext.getBuildConfig().COMPONENT_JAVA
    ],
    [
      stageName: 'Java 14 Smoke', target: 'test-junit-13-smoke-jenkins', javaVersion: 14, timeoutValue: 20,
      component: pipelineContext.getBuildConfig().COMPONENT_JAVA
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
      stageName: 'R3.5 Init Java 10', target: 'test-r-init', rVersion: '3.5.3', javaVersion: 10,
      timeoutValue: 10, hasJUnit: false, component: pipelineContext.getBuildConfig().COMPONENT_R,
      image: "${pipelineContext.getBuildConfig().DOCKER_REGISTRY}/opsh2oai/h2o-3/dev-r-3.5.3-jdk-10:${pipelineContext.getBuildConfig().DEFAULT_IMAGE_VERSION_TAG}"
    ],
    [
      stageName: 'R3.5 Init Java 11', target: 'test-r-init', rVersion: '3.5.3', javaVersion: 11,
      timeoutValue: 10, hasJUnit: false, component: pipelineContext.getBuildConfig().COMPONENT_R,
      image: "${pipelineContext.getBuildConfig().DOCKER_REGISTRY}/opsh2oai/h2o-3/dev-r-3.5.3-jdk-11:${pipelineContext.getBuildConfig().DEFAULT_IMAGE_VERSION_TAG}"
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
      stageName: 'Java 12 JUnit', target: 'test-junit-12-jenkins', pythonVersion: '2.7', javaVersion: 12,
      timeoutValue: 180, component: pipelineContext.getBuildConfig().COMPONENT_JAVA, additionalTestPackages: [pipelineContext.getBuildConfig().COMPONENT_PY]
    ],
    [
      stageName: 'Java 13 JUnit', target: 'test-junit-13-jenkins', pythonVersion: '2.7', javaVersion: 13,
      timeoutValue: 180, component: pipelineContext.getBuildConfig().COMPONENT_JAVA, additionalTestPackages: [pipelineContext.getBuildConfig().COMPONENT_PY]
    ],
    [
      stageName: 'Java 14 JUnit', target: 'test-junit-14-jenkins', pythonVersion: '2.7', javaVersion: 14,
      timeoutValue: 180, component: pipelineContext.getBuildConfig().COMPONENT_JAVA, additionalTestPackages: [pipelineContext.getBuildConfig().COMPONENT_PY]
    ],
    [
      stageName: 'Py2.7 Single Node', target: 'test-pyunit-single-node', pythonVersion: '2.7',
      timeoutValue: 40, component: pipelineContext.getBuildConfig().COMPONENT_PY
    ],
    [
      stageName: 'Py3.5 Single Node', target: 'test-pyunit-single-node', pythonVersion: '3.5',
      timeoutValue: 40, component: pipelineContext.getBuildConfig().COMPONENT_PY
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
      stageName: 'Py2.7 AutoML', target: 'test-pyunit-automl', pythonVersion: '2.7',
      timeoutValue: 90, component: pipelineContext.getBuildConfig().COMPONENT_PY
    ],
    [
      stageName: 'Py3.5 AutoML', target: 'test-pyunit-automl', pythonVersion: '3.5',
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
      stageName: 'R3.3 AutoML', target: 'test-r-automl', rVersion: '3.3.3',
      timeoutValue: 125, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'R3.3 Client Mode AutoML', target: 'test-r-client-mode-automl', rVersion: '3.3.3',
      timeoutValue: 155, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'R3.3 CMD Check', target: 'test-r-cmd-check', rVersion: '3.3.3',
      timeoutValue: 30, hasJUnit: false, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [
      stageName: 'R3.3 CMD Check as CRAN', target: 'test-r-cmd-check-as-cran', rVersion: '3.3.3',
      timeoutValue: 20, hasJUnit: false, component: pipelineContext.getBuildConfig().COMPONENT_R
    ],
    [ // These run with reduced number of file descriptors for early detection of FD leaks
      stageName: 'XGBoost Stress tests', target: 'test-pyunit-xgboost-stress', pythonVersion: '3.5', timeoutValue: 40,
      component: pipelineContext.getBuildConfig().COMPONENT_PY, customDockerArgs: [ '--ulimit nofile=150:150' ]
    ],
    [
      stageName: 'Kubernetes', target: 'test-h2o-k8s', timeoutValue: 20,
      component: pipelineContext.getBuildConfig().COMPONENT_JAVA,
      image: "${pipelineContext.getBuildConfig().DOCKER_REGISTRY}/opsh2oai/h2o-3-k8s:${pipelineContext.getBuildConfig().K8S_TEST_IMAGE_VERSION_TAG}",
      customDockerArgs: ['-v /var/run/docker.sock:/var/run/docker.sock', '--network host'], addToDockerGroup: true, nodeLabel: "micro"
    ]
  ]

  def supportedHadoopDists = pipelineContext.getBuildConfig().getSupportedHadoopDistributions()
  def HADOOP_STAGES = []
  for (distribution in supportedHadoopDists) {
    def target
    def ldapConfigPath
    if ((distribution.name == 'cdh' && distribution.version.startsWith('6.')) ||
            (distribution.name == 'hdp' && distribution.version.startsWith('3.'))){
      target = 'test-hadoop-3-smoke'
      ldapConfigPath = 'scripts/jenkins/config/ldap-jetty-9.txt'
    } else {
      target = 'test-hadoop-2-smoke'
      ldapConfigPath = 'scripts/jenkins/config/ldap-jetty-8.txt'
    }

    def stageTemplate = [
      target: target, timeoutValue: 60,
      component: pipelineContext.getBuildConfig().COMPONENT_ANY,
      additionalTestPackages: [
              pipelineContext.getBuildConfig().COMPONENT_PY,
              pipelineContext.getBuildConfig().COMPONENT_R
      ],
      customData: [
        distribution: distribution.name,
        version: distribution.version,
        commandFactory: 'h2o-3/scripts/jenkins/groovy/hadoopCommands.groovy',
        ldapConfigPath: ldapConfigPath,
        ldapConfigPathStandalone: 'scripts/jenkins/config/ldap-jetty-9.txt'
      ], pythonVersion: '2.7',
      customDockerArgs: [ '--privileged' ],
      executionScript: 'h2o-3/scripts/jenkins/groovy/hadoopStage.groovy',
      image: pipelineContext.getBuildConfig().getSmokeHadoopImage(distribution.name, distribution.version, false)
    ]
    def standaloneStage = evaluate(stageTemplate.inspect())
    standaloneStage.stageName = "${distribution.name.toUpperCase()} ${distribution.version} - STANDALONE"
    standaloneStage.customData.mode = 'STANDALONE'

    def onHadoopStage = evaluate(stageTemplate.inspect())
    onHadoopStage.stageName = "${distribution.name.toUpperCase()} ${distribution.version} - HADOOP"
    onHadoopStage.customData.mode = 'ON_HADOOP'

    HADOOP_STAGES += [ standaloneStage, onHadoopStage ]
  }

  def KERBEROS_STAGES = []
  def distributionsToTest = [
          [ name: "cdh", version: "5.10" ], // hdp2/hive1
          [ name: "cdh", version: "6.1"  ], // hdp3/hive2
          [ name: "hdp", version: "2.6"  ], // hdp2/hive2
          [ name: "hdp", version: "3.1"  ]  // hdp3/hive3 - JDBC Only
  ]
  // check our config is still valid
  for (distribution in distributionsToTest) {
    def distSupported = false
    for (supportedDist in supportedHadoopDists) {
      if (supportedDist == distribution) {
        distSupported = true
      }
    }
    if (!distSupported) {
      throw new IllegalArgumentException("Distribution ${distribution} is no longer supported. Update pipeline config.")
    }
    def target
    def ldapConfigPath
    if ((distribution.name == 'cdh' && distribution.version.startsWith('6.')) ||
            (distribution.name == 'hdp' && distribution.version.startsWith('3.'))){
      target = 'test-kerberos-hadoop-3'
      ldapConfigPath = 'scripts/jenkins/config/ldap-jetty-9.txt'
    } else {
      target = 'test-kerberos-hadoop-2'
      ldapConfigPath = 'scripts/jenkins/config/ldap-jetty-8.txt'
    }

    def stageTemplate = [
            target: target, timeoutValue: 60,
            component: pipelineContext.getBuildConfig().COMPONENT_ANY,
            additionalTestPackages: [
                    pipelineContext.getBuildConfig().COMPONENT_PY,
                    pipelineContext.getBuildConfig().COMPONENT_R
            ],
            customData: [
                    distribution: distribution.name,
                    version: distribution.version,
                    commandFactory: 'h2o-3/scripts/jenkins/groovy/kerberosCommands.groovy',
                    ldapConfigPath: ldapConfigPath,
                    kerberosUserName: 'jenkins@H2O.AI',
                    kerberosPrincipal: 'HTTP/localhost@H2O.AI',
                    kerberosConfigPath: 'scripts/jenkins/config/kerberos.conf',
                    spnegoConfigPath: 'scripts/jenkins/config/spnego.conf',
                    spnegoPropertiesPath: 'scripts/jenkins/config/spnego.properties',
            ], pythonVersion: '2.7',
            customDockerArgs: [ '--privileged' ],
            executionScript: 'h2o-3/scripts/jenkins/groovy/hadoopStage.groovy',
            image: pipelineContext.getBuildConfig().getSmokeHadoopImage(distribution.name, distribution.version, true)
    ]
    def standaloneStage = evaluate(stageTemplate.inspect())
    standaloneStage.stageName = "${distribution.name.toUpperCase()} ${distribution.version} - STANDALONE"
    standaloneStage.customData.mode = 'STANDALONE'

    def onHadoopStage = evaluate(stageTemplate.inspect())
    onHadoopStage.stageName = "${distribution.name.toUpperCase()} ${distribution.version} - HADOOP"
    onHadoopStage.customData.mode = 'ON_HADOOP'

    def onHadoopWithSpnegoStage = evaluate(stageTemplate.inspect())
    onHadoopWithSpnegoStage.stageName = "${distribution.name.toUpperCase()} ${distribution.version} - HADOOP WITH SPNEGO"
    onHadoopWithSpnegoStage.customData.mode = 'ON_HADOOP_WITH_SPNEGO'

    def steamDriverStage = evaluate(stageTemplate.inspect())
    steamDriverStage.stageName = "${distribution.name.toUpperCase()} ${distribution.version} - STEAM DRIVER"
    steamDriverStage.customData.mode = 'STEAM_DRIVER'

    def steamMapperStage = evaluate(stageTemplate.inspect())
    steamMapperStage.stageName = "${distribution.name.toUpperCase()} ${distribution.version} - STEAM MAPPER"
    steamMapperStage.customData.mode = 'STEAM_MAPPER'

    def sparklingStage = evaluate(stageTemplate.inspect())
    sparklingStage.stageName = "${distribution.name.toUpperCase()} ${distribution.version} - SPARKLING"
    sparklingStage.customData.mode = 'SPARKLING'

    def steamSparklingStage = evaluate(stageTemplate.inspect())
    steamSparklingStage.stageName = "${distribution.name.toUpperCase()} ${distribution.version} - STEAM SPARKLING"
    steamSparklingStage.customData.mode = 'STEAM_SPARKLING'

    KERBEROS_STAGES += [ standaloneStage, onHadoopStage, onHadoopWithSpnegoStage, steamDriverStage, steamMapperStage, sparklingStage, steamSparklingStage ]
  }

  final MULTINODE_CLUSTERS_CONFIGS = [
      [ distribution: "hdp", version: "2.2",
        nameNode: "mr-0xd6", hdpName: "hdp2_2_d", krb: false,
        hiveHost: "mr-0xd9.0xdata.loc",
        nodes: 4, xmx: "16G", extramem: "100",
        cloudingDir: "/user/jenkins/hadoop_multinode_tests"
      ],
      [ distribution: "hdp", version: "2.4",
        nameNode: "mr-0xg5", hdpName: "steam2", krb: true,
        hiveHost: "mr-0xg6.0xdata.loc", hivePrincipal: "hive/mr-0xg6.0xdata.loc@0XDATA.LOC",
        nodes: 4, xmx: "10G", extramem: "100",
        cloudingDir: "/user/jenkins/hadoop_multinode_tests"
      ]
  ]
  def HADOOP_MULTINODE_STAGES = []
  for (config in MULTINODE_CLUSTERS_CONFIGS) {
    def image = pipelineContext.getBuildConfig().getHadoopEdgeNodeImage(config.distribution, config.version, config.krb)
    def stage = [
            stageName: "TEST MULTINODE ${config.krb?"KRB ":""} ${config.distribution}${config.version}-${config.nameNode}",
            target: "test-hadoop-multinode", timeoutValue: 60,
            component: pipelineContext.getBuildConfig().COMPONENT_ANY,
            additionalTestPackages: [
                    pipelineContext.getBuildConfig().COMPONENT_PY,
                    pipelineContext.getBuildConfig().COMPONENT_R
            ],
            customData: config, pythonVersion: '2.7',
            executionScript: 'h2o-3/scripts/jenkins/groovy/hadoopMultinodeStage.groovy',
            image: image
    ]
    HADOOP_MULTINODE_STAGES += [ stage ]
  }
  HADOOP_MULTINODE_STAGES += [
      [
          stageName: "TEST External XGBoost on ${MULTINODE_CLUSTERS_CONFIGS[0].nameNode}",
          target: "test-steam-websocket", timeoutValue: 30,
          component: pipelineContext.getBuildConfig().COMPONENT_ANY,
          additionalTestPackages: [
                  pipelineContext.getBuildConfig().COMPONENT_PY
          ],
          customData: MULTINODE_CLUSTERS_CONFIGS[0], pythonVersion: '3.6',
          executionScript: 'h2o-3/scripts/jenkins/groovy/externalXGBoostStage.groovy',
          image: pipelineContext.getBuildConfig().getHadoopEdgeNodeImage(
                  MULTINODE_CLUSTERS_CONFIGS[0].distribution, MULTINODE_CLUSTERS_CONFIGS[0].version, MULTINODE_CLUSTERS_CONFIGS[0].krb
          )
      ]
  ]

  def XGB_STAGES = []
  for (String osName: pipelineContext.getBuildConfig().getSupportedXGBEnvironments().keySet()) {
    final def xgbEnvs = pipelineContext.getBuildConfig().getSupportedXGBEnvironments()[osName]
    xgbEnvs.each {xgbEnv ->
      final def stageDefinition = [
        stageName: "XGB on ${xgbEnv.name}", target: "test-xgb-smoke-${xgbEnv.targetName}-jenkins",
        timeoutValue: 15, component: pipelineContext.getBuildConfig().COMPONENT_ANY,
        additionalTestPackages: [pipelineContext.getBuildConfig().COMPONENT_JAVA], pythonVersion: '3.5',
        image: pipelineContext.getBuildConfig().getXGBImageForEnvironment(osName, xgbEnv),
        nodeLabel: xgbEnv.nodeLabel
      ]
      if (xgbEnv.targetName == pipelineContext.getBuildConfig().XGB_TARGET_GPU) {
        stageDefinition['customDockerArgs'] = ['--runtime=nvidia', '--pid=host']
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
  } else if (modeCode == MODE_KERBEROS_CODE) {
    executeInParallel(KERBEROS_STAGES, pipelineContext)
  } else if (modeCode == MODE_HADOOP_MULTINODE_CODE) {
    executeInParallel(HADOOP_MULTINODE_STAGES, pipelineContext)
  } else if (modeCode == MODE_XGB_CODE) {
    executeInParallel(XGB_STAGES, pipelineContext)
  } else if (modeCode == MODE_COVERAGE_CODE) {
    executeInParallel(COVERAGE_STAGES, pipelineContext)
  } else if (modeCode == MODE_SINGLE_TEST_CODE) {
    executeInParallel(SINGLE_TEST_STAGES, pipelineContext)
  } else {
    def jobs = PR_STAGES
    if (modeCode >= MODE_MASTER_CODE) {
      jobs += MASTER_STAGES
    }
    if (modeCode >= MODE_NIGHTLY_CODE) {
      jobs += NIGHTLY_STAGES
    }
    if (modeCode >= MODE_NIGHTLY_CODE) {
      // in Nightly mode execute all jobs regardless whether smoke tests fail 
      executeInParallel(SMOKE_STAGES + jobs, pipelineContext)
    } else {
      executeInParallel(SMOKE_STAGES, pipelineContext)
      executeInParallel(jobs, pipelineContext)
    }
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
          imageSpecifier = c['imageSpecifier']
          dockerImageSuffix = c['dockerImageSuffix']
          healthCheckSuppressed = c['healthCheckSuppressed']
          addToDockerGroup = c['addToDockerGroup']
        }
      }
    ]
  })
}

private void invokeStage(final pipelineContext, final body) {

  final String DEFAULT_JAVA = '8'
  final String DEFAULT_PYTHON = '3.5'
  final String DEFAULT_R = '3.5.3'
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
  if (config.healthCheckSuppressed == null) {
    config.healthCheckSuppressed = false
  }
  if (config.healthCheckSuppressed) {
    echo "######### Healthcheck suppressed #########"
  }

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
          try {
            while (!healthCheckPassed) {
              attempt += 1
              if (attempt > HEALTH_CHECK_RETRIES) {
                error "Too many attempts to pass initial health check"
              }
              String nodeLabel = pipelineContext.getHealthChecker().getHealthyNodesLabel(config.nodeLabel)
              echo "######### NodeLabel: ${nodeLabel} #########"
              node(nodeLabel) {
                echo "###### Unstash scripts. ######"
                pipelineContext.getUtils().unstashScripts(this)

                healthCheckPassed = config.healthCheckSuppressed || pipelineContext.getHealthChecker().checkHealth(this, env.NODE_NAME, config.image, pipelineContext.getBuildConfig().DOCKER_REGISTRY, pipelineContext.getBuildConfig())
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
