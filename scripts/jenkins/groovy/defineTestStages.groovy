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
  def MODE_NIGHTLY_REPEATED_CODE = 15
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
    [name: 'MODE_NIGHTLY_REPEATED', code: MODE_NIGHTLY_REPEATED_CODE],
    [name: 'MODE_NIGHTLY', code: MODE_NIGHTLY_CODE]
  ]
  def config = pipelineContext.getBuildConfig()
  def v = config.VERSIONS
  def modeCode = MODES.find{it['name'] == config.getMode()}['code']

  def METADATA_VALIDATION_STAGES = [
    [
      stageName: 'Check Pull Request Metadata', 
      target: 'check-pull-request', 
      javaVersion: v.JAVA.first_LTS, 
      timeoutValue: 10,
      component: config.COMPONENT_ANY
    ]
  ]

  // Job will execute PR_STAGES only if these are green.
  // for Python, smoke only oldest and latest supported versions
  def SMOKE_STAGES = [
    [
      stageName: "Py${v.PYTHON.legacy} Smoke",
      target: 'test-py-smoke', 
      pythonVersion: v.PYTHON.legacy, 
      timeoutValue: 8,
      component: config.COMPONENT_PY
    ],
    [
      stageName: "Py${v.PYTHON.latest} Smoke", 
      target: 'test-py-smoke', 
      pythonVersion: v.PYTHON.latest, 
      timeoutValue: 8,
      component: config.COMPONENT_PY
    ],
    [
      stageName: 'R3 Smoke', 
      target: 'test-r-smoke', 
      rVersion: v.R.latest_3,
      timeoutValue: 8,
      component: config.COMPONENT_R
    ],
    [
      stageName: 'R4 Smoke', 
      target: 'test-r-smoke', 
      rVersion: v.R.latest_4,
      timeoutValue: 8,
      component: config.COMPONENT_R
    ],
    [
      stageName: 'Flow Headless Smoke', 
      target: 'test-flow-headless-smoke',
      timeoutValue: 20,
      component: config.COMPONENT_JS
    ],
    [
      stageName: "Java ${v.JAVA.first_LTS} Smoke",
      target: 'test-junit-smoke-jenkins', 
      javaVersion: v.JAVA.first_LTS, 
      timeoutValue: 20,
      component: config.COMPONENT_JAVA
    ]
  ]

  // Stages executed after each push to PR branch.
  // for Python, mainly test with latest supported version
  def PR_STAGES = [
    [
      stageName: "Py${v.PYTHON.latest} Smoke (Minimal Assembly)", 
      target: 'test-py-smoke-minimal', 
      pythonVersion: v.PYTHON.latest, 
      timeoutValue: 8,
      component: config.COMPONENT_PY,
      additionalTestPackages: [config.COMPONENT_MINIMAL]
    ],
    [
      stageName: "Java ${v.JAVA.first_LTS} RuleFit", 
      target: 'test-junit-rulefit-jenkins', 
      pythonVersion: v.PYTHON.legacy, 
      javaVersion: v.JAVA.first_LTS,
      timeoutValue: 180, 
      component: config.COMPONENT_JAVA, 
      additionalTestPackages: [config.COMPONENT_PY],
      imageSpecifier: "python-${v.PYTHON.legacy}-jdk-${v.JAVA.first_LTS}"
    ],
    [
      stageName: "Py${v.PYTHON.legacy} Booklets", 
      target: 'test-py-booklets', 
      pythonVersion: v.PYTHON.legacy,
      timeoutValue: 40, 
      component: config.COMPONENT_PY
    ],
    [
      stageName: "Py${v.PYTHON.legacy} Init Java ${v.JAVA.first_LTS}", 
      target: 'test-py-init', 
      pythonVersion: v.PYTHON.legacy, 
      javaVersion: v.JAVA.first_LTS,
      timeoutValue: 10, 
      hasJUnit: false, 
      component: config.COMPONENT_PY,
      imageSpecifier: "python-${v.PYTHON.legacy}-jdk-${v.JAVA.first_LTS}"
    ],
    [
      stageName: "Py${v.PYTHON.latest} Single Node", 
      target: 'test-pyunit-single-node', 
      pythonVersion: v.PYTHON.latest,
      timeoutValue: 40, 
      component: config.COMPONENT_PY
    ],
    [
      stageName: "Py${v.PYTHON.latest} Small", 
      target: 'test-pyunit-small', 
      pythonVersion: v.PYTHON.latest,
      timeoutValue: 90, 
      component: config.COMPONENT_PY
    ],
    [
      stageName: "Py${v.PYTHON.latest} AutoML", 
      target: 'test-pyunit-automl', 
      pythonVersion: v.PYTHON.latest,
      timeoutValue: 90, 
      component: config.COMPONENT_PY
    ],
    [
      stageName: "Py${v.PYTHON.latest} AutoML Smoke (NO XGB)", 
      target: 'test-pyunit-automl-smoke-noxgb', 
      pythonVersion: v.PYTHON.latest,
      timeoutValue: 20, 
      component: config.COMPONENT_PY
    ],
    [
      stageName: "Py${v.PYTHON.latest} Fault Tolerance", 
      target: 'test-pyunit-fault-tolerance', 
      pythonVersion: v.PYTHON.latest,
      timeoutValue: 30, 
      component: config.COMPONENT_PY
    ],
    [
      stageName: "R3 Init Java ${v.JAVA.first_LTS}", 
      target: 'test-r-init', 
      rVersion: v.R.latest_3, 
      javaVersion: v.JAVA.first_LTS,
      timeoutValue: 10, hasJUnit: false, 
      component: config.COMPONENT_R,
      imageSpecifier: "r-${v.R.latest_3}-jdk-${v.JAVA.first_LTS}"
    ],
    [
      stageName: 'R3 Small', 
      target: 'test-r-small', 
      rVersion: v.R.latest_3,
      timeoutValue: 125, 
      component: config.COMPONENT_R
    ],
    [
      stageName: 'R3 AutoML', 
      target: 'test-r-automl', 
      rVersion: v.R.latest_3,
      timeoutValue: 125, 
      component: config.COMPONENT_R
    ],
    [
      stageName: 'R3 CMD Check', 
      target: 'test-r-cmd-check', 
      rVersion: v.R.latest_3,
      timeoutValue: 90, 
      hasJUnit: false, 
      component: config.COMPONENT_R
    ],
    [
      stageName: 'R3 CMD Check as CRAN', 
      target: 'test-r-cmd-check-as-cran', 
      rVersion: v.R.latest_3,
      timeoutValue: 20, 
      hasJUnit: false, 
      component: config.COMPONENT_R
    ],
    [
      stageName: 'R4 Small', 
      target: 'test-r-small', 
      rVersion: v.R.latest_4,
      timeoutValue: 125, 
      component: config.COMPONENT_R
    ],
    [
      stageName: 'R4 CMD Check as CRAN', 
      target: 'test-r-cmd-check-as-cran', 
      rVersion: v.R.latest_4,
      timeoutValue: 20, 
      hasJUnit: false, 
      component: config.COMPONENT_R
    ],
    [
      stageName: 'R3 Booklets', 
      target: 'test-r-booklets', 
      rVersion: v.R.latest_3,
      timeoutValue: 50, 
      component: config.COMPONENT_R
    ],
    [
      stageName: 'R3 Demos Small', 
      target: 'test-r-demos-small', 
      rVersion: v.R.latest_3,
      timeoutValue: 15, 
      component: config.COMPONENT_R
    ],
    [
      stageName: 'Flow Headless', 
      target: 'test-flow-headless',
      timeoutValue: 75, 
      component: config.COMPONENT_JS
    ],
    [
      stageName: "Py${v.PYTHON.latest} Medium-large", 
      target: 'test-pyunit-medium-large', 
      pythonVersion: v.PYTHON.latest,
      timeoutValue: 150, 
      component: config.COMPONENT_PY
    ],
    [
      stageName: 'R3 Medium-large', 
      target: 'test-r-medium-large', 
      rVersion: v.R.latest_3,
      timeoutValue: 80, 
      component: config.COMPONENT_R
    ],
    [
      stageName: 'R3 Demos Medium-large', 
      target: 'test-r-demos-medium-large', 
      rVersion: v.R.latest_3,
      timeoutValue: 140, 
      component: config.COMPONENT_R
    ],
    [
      stageName: 'INFO Check', 
      target: 'test-info',
      timeoutValue: 10, 
      component: config.COMPONENT_ANY, 
      additionalTestPackages: [config.COMPONENT_R]
    ],
    [
      stageName: "Py${v.PYTHON.legacy} Demos", 
      target: 'test-py-demos', 
      pythonVersion: v.PYTHON.legacy,
      timeoutValue: 60, 
      component: config.COMPONENT_PY
    ],
    [
      stageName: "Py${v.PYTHON.latest} Test Demos", 
      target: 'test-pyunit-demos', 
      pythonVersion: v.PYTHON.latest,
      timeoutValue: 10, 
      component: config.COMPONENT_PY
    ],
    [
      stageName: "Java ${v.JAVA.first_LTS} JUnit", 
      target: 'test-junit-jenkins', 
      pythonVersion: v.PYTHON.legacy, 
      javaVersion: v.JAVA.first_LTS,
      timeoutValue: 180, 
      component: config.COMPONENT_JAVA, 
      additionalTestPackages: [config.COMPONENT_PY],
      imageSpecifier: "python-${v.PYTHON.legacy}-jdk-${v.JAVA.first_LTS}"
    ],
    [
      stageName: "Java ${v.JAVA.first_LTS} Core JUnit",
      target: 'test-junit-core-jenkins',
      pythonVersion: v.PYTHON.legacy,
      javaVersion: v.JAVA.first_LTS,
      timeoutValue: 180,
      component: config.COMPONENT_JAVA,
      additionalTestPackages: [config.COMPONENT_PY],
      imageSpecifier: "python-${v.PYTHON.legacy}-jdk-${v.JAVA.first_LTS}"
    ],
          
    [
      stageName: 'REST Smoke Test', 
      target: 'test-rest-smoke', 
      pythonVersion: v.PYTHON.legacy, 
      javaVersion: v.JAVA.first_LTS,
      timeoutValue: 180, 
      component: config.COMPONENT_JAVA, 
      additionalTestPackages: [config.COMPONENT_PY],
      imageSpecifier: "python-${v.PYTHON.legacy}-jdk-${v.JAVA.first_LTS}"
    ],
    [
      stageName: "Java ${v.JAVA.first_LTS} AutoML JUnit", 
      target: 'test-junit-automl-jenkins', 
      pythonVersion: v.PYTHON.legacy, 
      javaVersion: v.JAVA.first_LTS,
      timeoutValue: 120, 
      component: config.COMPONENT_JAVA, 
      additionalTestPackages: [config.COMPONENT_PY],
      imageSpecifier: "python-${v.PYTHON.legacy}-jdk-${v.JAVA.first_LTS}"
    ],
    [
      stageName: "Java ${v.JAVA.first_LTS} Clustering JUnit",
      target: 'test-junit-clustering-jenkins', 
      pythonVersion: v.PYTHON.legacy, 
      javaVersion: v.JAVA.first_LTS,
      timeoutValue: 20, 
      component: config.COMPONENT_JAVA, 
      additionalTestPackages: [config.COMPONENT_PY],
      imageSpecifier: "python-${v.PYTHON.legacy}-jdk-${v.JAVA.first_LTS}"
    ],
    [
      stageName: "Java ${v.JAVA.first_LTS} XGBoost Multinode JUnit", 
      target: 'test-junit-xgb-multi-jenkins', 
      pythonVersion: v.PYTHON.legacy, 
      javaVersion: v.JAVA.first_LTS,
      timeoutValue: 120, 
      component: config.COMPONENT_JAVA, 
      additionalTestPackages: [config.COMPONENT_PY],
      imageSpecifier: "python-${v.PYTHON.legacy}-jdk-${v.JAVA.first_LTS}"
    ],
    [
      stageName: "R3 Generate Docs", 
      target: 'r-generate-docs-jenkins', 
      archiveFiles: false,
      timeoutValue: 10, 
      component: config.COMPONENT_R, 
      hasJUnit: false,
      archiveAdditionalFiles: ['r-generated-docs.zip'], 
      installRPackage: false
    ],
    [
      stageName: "MOJO Compatibility (Java ${v.JAVA.mojo})", 
      target: 'test-mojo-compatibility',
      archiveFiles: false, 
      timeoutValue: 20, 
      hasJUnit: false, 
      pythonVersion: v.PYTHON.first, 
      javaVersion: v.JAVA.mojo,
      component: config.COMPONENT_JAVA, // only run when Java changes (R/Py cannot affect mojo)
      imageSpecifier: "mojocompat",
      additionalTestPackages: [config.COMPONENT_PY]
    ],
  ]

  def BENCHMARK_STAGES = [
    [
      stageName: 'GBM Benchmark', 
      executionScript: 'h2o-3/scripts/jenkins/groovy/benchmarkStage.groovy',
      timeoutValue: 120, 
      target: 'benchmark', 
      component: config.COMPONENT_ANY,
      additionalTestPackages: [config.COMPONENT_R],
      customData: [algorithm: 'gbm'], 
      makefilePath: config.BENCHMARK_MAKEFILE_PATH,
      nodeLabel: config.getBenchmarkNodeLabel(),
      healthCheckSuppressed: true
    ],
    [
      stageName: 'GLM Benchmark', 
      executionScript: 'h2o-3/scripts/jenkins/groovy/benchmarkStage.groovy',
      timeoutValue: 120, 
      target: 'benchmark', 
      component: config.COMPONENT_ANY,
      additionalTestPackages: [config.COMPONENT_R],
      customData: [algorithm: 'glm'], 
      makefilePath: config.BENCHMARK_MAKEFILE_PATH,
      nodeLabel: config.getBenchmarkNodeLabel(),
      healthCheckSuppressed: true
    ],
    [ 
      stageName: 'GAM Benchmark', 
      executionScript: 'h2o-3/scripts/jenkins/groovy/benchmarkStage.groovy',
      timeoutValue: 120, 
      target: 'benchmark', 
      component: config.COMPONENT_ANY,
      additionalTestPackages: [config.COMPONENT_R],
      customData: [algorithm: 'gam'], 
      makefilePath: config.BENCHMARK_MAKEFILE_PATH,
      nodeLabel: config.getBenchmarkNodeLabel(),
      healthCheckSuppressed: true
    ],
    [
      stageName: 'H2O XGB Benchmark', 
      executionScript: 'h2o-3/scripts/jenkins/groovy/benchmarkStage.groovy',
      timeoutValue: 120, 
      target: 'benchmark', 
      component: config.COMPONENT_ANY,
      additionalTestPackages: [config.COMPONENT_R],
      customData: [algorithm: 'xgb'], 
      makefilePath: config.BENCHMARK_MAKEFILE_PATH,
      nodeLabel: config.getBenchmarkNodeLabel(),
      healthCheckSuppressed: true
    ],
    [
      stageName: 'H2O XGB GPU Benchmark',
      executionScript: 'h2o-3/scripts/jenkins/groovy/benchmarkStage.groovy',
      customDockerArgs: ['--runtime=nvidia', '--pid=host'],
      timeoutValue: 120, 
      target: 'benchmark-xgb-gpu', 
      component: config.COMPONENT_ANY,
      additionalTestPackages: [config.COMPONENT_R],
      customData: [algorithm: 'xgb'], 
      makefilePath: config.BENCHMARK_MAKEFILE_PATH,
      nodeLabel: config.getGPUBenchmarkNodeLabel(),
      healthCheckSuppressed: true
    ],
    [
      stageName: 'Vanilla XGB Benchmark', 
      executionScript: 'h2o-3/scripts/jenkins/groovy/benchmarkStage.groovy',
      timeoutValue: 120, 
      target: 'benchmark-xgb-vanilla', 
      component: config.COMPONENT_ANY,
      additionalTestPackages: [config.COMPONENT_PY],
      customData: [algorithm: 'xgb-vanilla'], 
      makefilePath: config.BENCHMARK_MAKEFILE_PATH,
      nodeLabel: config.getBenchmarkNodeLabel(),
      healthCheckSuppressed: true
    ],
    [
      stageName: 'DMLC XGB Benchmark', 
      executionScript: 'h2o-3/scripts/jenkins/groovy/benchmarkStage.groovy',
      timeoutValue: 120, 
      target: 'benchmark-dmlc-r-xgboost', 
      component: config.COMPONENT_ANY,
      additionalTestPackages: [config.COMPONENT_R],
      customData: [algorithm: 'xgb-dmlc'], 
      makefilePath: config.BENCHMARK_MAKEFILE_PATH,
      nodeLabel: config.getBenchmarkNodeLabel(),
      healthCheckSuppressed: true
    ],
    [ 
      stageName: 'MERGE Benchmark', 
      executionScript: 'h2o-3/scripts/jenkins/groovy/benchmarkStage.groovy',
      timeoutValue: 120, 
      target: 'benchmark', 
      component: config.COMPONENT_ANY,
      additionalTestPackages: [config.COMPONENT_R],
      customData: [algorithm: 'merge'], 
      makefilePath: config.BENCHMARK_MAKEFILE_PATH,
      nodeLabel: config.getBenchmarkNodeLabel(),
      healthCheckSuppressed: true
    ],
    [
      stageName: 'SORT Benchmark', 
      executionScript: 'h2o-3/scripts/jenkins/groovy/benchmarkStage.groovy',
      timeoutValue: 120, 
      target: 'benchmark', 
      component: config.COMPONENT_ANY,
      additionalTestPackages: [config.COMPONENT_R],
      customData: [algorithm: 'sort'], 
      makefilePath: config.BENCHMARK_MAKEFILE_PATH,
      nodeLabel: config.getBenchmarkNodeLabel(),
      healthCheckSuppressed: true
    ],
    [
      stageName: 'Rulefit Benchmark', 
      executionScript: 'h2o-3/scripts/jenkins/groovy/benchmarkStage.groovy',
      timeoutValue: 120, 
      target: 'benchmark', 
      component: config.COMPONENT_ANY,
      additionalTestPackages: [config.COMPONENT_R],
      customData: [algorithm: 'rulefit'], 
      makefilePath: config.BENCHMARK_MAKEFILE_PATH,
      nodeLabel: config.getBenchmarkNodeLabel(),
      healthCheckSuppressed: true
    ],
  ]

  // Stages executed in addition to PR_STAGES after merge to master.
  def MASTER_STAGES = [
    [
      stageName: 'R3 Datatable', 
      target: 'test-r-datatable', 
      rVersion: v.R.latest_3,
      timeoutValue: 40, 
      component: config.COMPONENT_R
    ],
    [
      stageName: 'Flow Headless Small', 
      target: 'test-flow-headless-small',
      timeoutValue: 75, 
      component: config.COMPONENT_JS
    ],
    [
      stageName: 'Flow Headless Medium',
      target: 'test-flow-headless-medium',
      timeoutValue: 75, 
      component: config.COMPONENT_JS
    ]
  ]

  // Stages executed in addition to MASTER_STAGES, used for repeated nightly builds.
  // Stages in this group are executed several times at night. The goal is to catch
  // possible rare bugs - these are expected to happen mainly in the Java backend,
  // we can thus limit the Py/R versions to just a single version (the most common in use).
  // Should contain any stages that are flaky (eg. the "init" stages).
  def NIGHTLY_REPEATED_STAGES = [
    [
      stageName: "Py${v.PYTHON.legacy} Init Java ${v.JAVA.latest_LTS}", 
      target: 'test-py-init', 
      pythonVersion: v.PYTHON.legacy, 
      javaVersion: v.JAVA.latest_LTS,
      timeoutValue: 10, 
      hasJUnit: false,
      component: config.COMPONENT_PY,
      imageSpecifier: "python-${v.PYTHON.legacy}-jdk-${v.JAVA.latest_LTS}"
    ],
    [
      stageName: "R3 Init Java ${v.JAVA.latest_LTS}", 
      target: 'test-r-init', 
      rVersion: v.R.latest_3, 
      javaVersion: v.JAVA.latest_LTS,
      timeoutValue: 10, 
      hasJUnit: false, 
      component: config.COMPONENT_R,
      imageSpecifier: "r-${v.R.latest_3}-jdk-${v.JAVA.latest_LTS}"
    ],
    [
      stageName: 'Java 14 JUnit', 
      target: 'test-junit-1x-jenkins', 
      pythonVersion: v.PYTHON.legacy, 
      javaVersion: 14,
      timeoutValue: 180, 
      component: config.COMPONENT_JAVA, 
      additionalTestPackages: [config.COMPONENT_PY],
      imageSpecifier: "python-${v.PYTHON.legacy}-jdk-14"
    ],
    [
      stageName: 'Java 15 JUnit', 
      target: 'test-junit-1x-jenkins', 
      pythonVersion: v.PYTHON.legacy, 
      javaVersion: 15,
      timeoutValue: 180, 
      component: config.COMPONENT_JAVA,
      additionalTestPackages: [config.COMPONENT_PY],
      imageSpecifier: "python-${v.PYTHON.legacy}-jdk-15"
    ],
    [
      stageName: "Py${v.PYTHON.active} Single Node", 
      target: 'test-pyunit-single-node', 
      pythonVersion: v.PYTHON.active,
      timeoutValue: 40, 
      component: config.COMPONENT_PY
    ],
    [
      stageName: "Py${v.PYTHON.active} Small", 
      target: 'test-pyunit-small', 
      pythonVersion: v.PYTHON.active,
      timeoutValue: 90, 
      component: config.COMPONENT_PY
    ],
    [
      stageName: "Py${v.PYTHON.active} Fault Tolerance", 
      target: 'test-pyunit-fault-tolerance', 
      pythonVersion: v.PYTHON.active,
      timeoutValue: 30, 
      component: config.COMPONENT_PY
    ],
    [
      stageName: "Py${v.PYTHON.active} AutoML", 
      target: 'test-pyunit-automl', 
      pythonVersion: v.PYTHON.active,
      timeoutValue: 90, 
      component: config.COMPONENT_PY
    ],
    [
      stageName: "Py${v.PYTHON.active} Medium-large", 
      target: 'test-pyunit-medium-large', 
      pythonVersion: v.PYTHON.active,
      timeoutValue: 150, 
      component: config.COMPONENT_PY
    ],
    [
      stageName: 'R3.3 Medium-large', 
      target: 'test-r-medium-large', 
      rVersion: '3.3.3',
      timeoutValue: 80, 
      component: config.COMPONENT_R
    ],
    [
      stageName: 'R3.3 Small', 
      target: 'test-r-small', 
      rVersion: '3.3.3',
      timeoutValue: 125, 
      component: config.COMPONENT_R
    ],
    [
      stageName: 'R3.3 AutoML', 
      target: 'test-r-automl', 
      rVersion: '3.3.3',
      timeoutValue: 125, 
      component: config.COMPONENT_R
    ],
    [
      stageName: 'Kubernetes', 
      target: 'test-h2o-k8s', 
      timeoutValue: 20, 
      activatePythonEnv: false,
      component: config.COMPONENT_JAVA,
      image: "${config.DOCKER_REGISTRY}/opsh2oai/h2o-3-k8s:${config.K8S_TEST_IMAGE_VERSION_TAG}",
      customDockerArgs: ['-v /var/run/docker.sock:/var/run/docker.sock', '--network host'], 
      addToDockerGroup: true, 
      nodeLabel: "micro"
    ]
  ]

  // Stages executed in addition to NIGHTLY_REPEATED_STAGES, executed once a night.
  // Should contain all Java versions and also the minimum supported Python version. 
  def NIGHTLY_STAGES = [] +
          + v.JAVA.smoke_tests.collect {
            [
              stageName: "Java ${it} Smoke",
              target: 'test-junit-smoke-jenkins',
              javaVersion: it,
              timeoutValue: 20,
              component: config.COMPONENT_JAVA
            ]
          }
          + v.JAVA.lts_tests.collect {
            [
              stageName: "Java ${it} JUnit",
              target: 'test-junit-1x-jenkins',
              pythonVersion: v.PYTHON.legacy,
              javaVersion: it,
              timeoutValue: 180,
              component: config.COMPONENT_JAVA,
              additionalTestPackages: [config.COMPONENT_PY],
              imageSpecifier: "python-${v.PYTHON.legacy}-jdk-${it}"
            ]
          }
          + [
              [
                stageName: "Py${v.PYTHON.legacy} Single Node", 
                target: 'test-pyunit-single-node', 
                pythonVersion: v.PYTHON.legacy,
                timeoutValue: 40, 
                component: config.COMPONENT_PY
              ],
              [
                stageName: "Py${v.PYTHON.legacy} Small", 
                target: 'test-pyunit-small', 
                pythonVersion: v.PYTHON.legacy,
                timeoutValue: 90, 
                component: config.COMPONENT_PY
              ],
              [
                stageName: "Py${v.PYTHON.legacy} Fault Tolerance", 
                target: 'test-pyunit-fault-tolerance', 
                pythonVersion: v.PYTHON.legacy,
                timeoutValue: 30, component: config.COMPONENT_PY
              ],
              [
                stageName: "Py${v.PYTHON.legacy} AutoML", 
                target: 'test-pyunit-automl', 
                pythonVersion: v.PYTHON.legacy,
                timeoutValue: 90, 
                component: config.COMPONENT_PY
              ],
              [
                stageName: "Py${v.PYTHON.legacy} Medium-large", 
                target: 'test-pyunit-medium-large', 
                pythonVersion: v.PYTHON.legacy,
                timeoutValue: 150, 
                component: config.COMPONENT_PY
              ],
              [
                stageName: 'R3 Small Client Mode', 
                target: 'test-r-small-client-mode', 
                rVersion: v.R.latest_3,
                timeoutValue: 155, 
                component: config.COMPONENT_R
              ],
              [
                stageName: 'R3 Client Mode AutoML', 
                target: 'test-r-client-mode-automl', 
                rVersion: v.R.latest_3,
                timeoutValue: 155, 
                component: config.COMPONENT_R
              ],
              [
                stageName: 'R3 Small Client Mode Disconnect Attack', 
                target: 'test-r-small-client-mode-attack', 
                rVersion: v.R.latest_3,
                timeoutValue: 155, 
                component: config.COMPONENT_R
              ],
              [ // These run with reduced number of file descriptors for early detection of FD leaks
                stageName: 'XGBoost Stress tests', 
                target: 'test-pyunit-xgboost-stress', 
                pythonVersion: v.PYTHON.active, 
                timeoutValue: 40,
                component: config.COMPONENT_PY, 
                customDockerArgs: [ '--ulimit nofile=150:150' ]
              ]
            ]

  def supportedHadoopDists = config.getSupportedHadoopDistributions()
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
      component: config.COMPONENT_ANY,
      additionalTestPackages: [
              config.COMPONENT_PY,
              config.COMPONENT_R
      ],
      customData: [
        distribution: distribution.name,
        version: distribution.version,
        commandFactory: 'h2o-3/scripts/jenkins/groovy/hadoopCommands.groovy',
        ldapConfigPath: ldapConfigPath,
        ldapConfigPathStandalone: 'scripts/jenkins/config/ldap-jetty-9.txt'
      ], 
      pythonVersion: v.PYTHON.legacy,
      customDockerArgs: [ '--privileged' ],
      executionScript: 'h2o-3/scripts/jenkins/groovy/hadoopStage.groovy',
      image: config.getSmokeHadoopImage(distribution.name, distribution.version, false)
    ]
    def standaloneStage = evaluate(stageTemplate.inspect())
    standaloneStage.stageName = "${distribution.name.toUpperCase()} ${distribution.version} - STANDALONE"
    standaloneStage.customData.mode = 'STANDALONE'

    def onHadoopStage = evaluate(stageTemplate.inspect())
    onHadoopStage.stageName = "${distribution.name.toUpperCase()} ${distribution.version} - HADOOP"
    onHadoopStage.customData.mode = 'ON_HADOOP'

    if (distribution.name == 'cdh' && distribution.version.startsWith('6.3')) {
      def onHadoopStageJava11 = evaluate(stageTemplate.inspect())
      onHadoopStageJava11.stageName = "${distribution.name.toUpperCase()} ${distribution.version} - HADOOP - Java ${v.JAVA.latest_LTS} (Hash Login)"
      onHadoopStageJava11.customData.mode = 'ON_HADOOP'
      onHadoopStageJava11.javaVersion = v.JAVA.latest_LTS
      onHadoopStageJava11.customData.customAuth = '-hash_login -login_conf /tmp/hash.login'
      HADOOP_STAGES += [ onHadoopStageJava11 ]
    }

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
            component: config.COMPONENT_ANY,
            additionalTestPackages: [
                    config.COMPONENT_PY,
                    config.COMPONENT_R
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
            ], 
            pythonVersion: v.PYTHON.legacy,
            customDockerArgs: [ '--privileged' ],
            executionScript: 'h2o-3/scripts/jenkins/groovy/hadoopStage.groovy',
            image: config.getSmokeHadoopImage(distribution.name, distribution.version, true)
    ]
    def standaloneStage = evaluate(stageTemplate.inspect())
    standaloneStage.stageName = "${distribution.name.toUpperCase()} ${distribution.version} - STANDALONE"
    standaloneStage.customData.mode = 'STANDALONE'

    def standaloneKeytabStage = evaluate(stageTemplate.inspect())
    standaloneKeytabStage.stageName = "${distribution.name.toUpperCase()} ${distribution.version} - STANDALONE KEYTAB"
    standaloneKeytabStage.customData.mode = 'STANDALONE_KEYTAB'

    def onHadoopStage = evaluate(stageTemplate.inspect())
    onHadoopStage.stageName = "${distribution.name.toUpperCase()} ${distribution.version} - HADOOP"
    onHadoopStage.customData.mode = 'ON_HADOOP'

    def onHadoopWithSpnegoStage = evaluate(stageTemplate.inspect())
    onHadoopWithSpnegoStage.stageName = "${distribution.name.toUpperCase()} ${distribution.version} - HADOOP WITH SPNEGO"
    onHadoopWithSpnegoStage.customData.mode = 'ON_HADOOP_WITH_SPNEGO'

    def onHadoopWithHdfsTokenRefreshStage = evaluate(stageTemplate.inspect())
    onHadoopWithHdfsTokenRefreshStage.stageName = "${distribution.name.toUpperCase()} ${distribution.version} - HADOOP WITH HDFS TOKEN REFRESH"
    onHadoopWithHdfsTokenRefreshStage.customData.mode = 'ON_HADOOP_WITH_HDFS_TOKEN_REFRESH'

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

    KERBEROS_STAGES += [ standaloneStage, standaloneKeytabStage, 
                         onHadoopStage, onHadoopWithSpnegoStage, onHadoopWithHdfsTokenRefreshStage, 
                         steamDriverStage, steamMapperStage, 
                         sparklingStage, steamSparklingStage ]
  }

  final MULTINODE_CLUSTERS_CONFIGS = [
      [ distribution: "hdp", 
        version: "2.2",
        nameNode: "mr-0xd4", 
        configSource: "mr-0xd6", 
        hdpName: "hdp2_2_d", 
        krb: false,
        hiveHost: "mr-0xd9.0xdata.loc",
        nodes: 4, 
        xmx: "16G", 
        extramem: "100",
        cloudingDir: "/user/jenkins/hadoop_multinode_tests"
      ],
      [ distribution: "hdp", 
        version: "2.4",
        nameNode: "mr-0xg5", 
        configSource: "mr-0xg5", 
        hdpName: "steam2", 
        krb: true,
        hiveHost: "mr-0xg6.0xdata.loc", 
        hivePrincipal: "hive/mr-0xg6.0xdata.loc@0XDATA.LOC",
        nodes: 4, 
        xmx: "10G", 
        extramem: "100",
        cloudingDir: "/user/jenkins/hadoop_multinode_tests"
      ]
  ]
  def HADOOP_MULTINODE_STAGES = []
  for (cluster in MULTINODE_CLUSTERS_CONFIGS) {
    def image = config.getHadoopEdgeNodeImage(cluster.distribution, cluster.version, cluster.krb)
    def stage = [
            stageName: "TEST MULTINODE ${cluster.krb?"KRB ":""} ${cluster.distribution}${cluster.version}-${cluster.nameNode}",
            target: "test-hadoop-multinode", 
            timeoutValue: 60,
            component: config.COMPONENT_ANY,
            additionalTestPackages: [
                    config.COMPONENT_PY,
                    config.COMPONENT_R
            ],
            customData: cluster, 
            pythonVersion: v.PYTHON.legacy,
            executionScript: 'h2o-3/scripts/jenkins/groovy/hadoopMultinodeStage.groovy',
            image: image
    ]
    HADOOP_MULTINODE_STAGES += [ stage ]
  }
  HADOOP_MULTINODE_STAGES += [
      [
          stageName: "TEST External XGBoost on ${MULTINODE_CLUSTERS_CONFIGS[0].nameNode}",
          target: "test-steam-websocket", timeoutValue: 30,
          component: config.COMPONENT_ANY,
          additionalTestPackages: [
                  config.COMPONENT_PY
          ],
          customData: MULTINODE_CLUSTERS_CONFIGS[0], 
          pythonVersion: v.PYTHON.active,
          executionScript: 'h2o-3/scripts/jenkins/groovy/externalXGBoostStage.groovy',
          image: config.getHadoopEdgeNodeImage(
                  MULTINODE_CLUSTERS_CONFIGS[0].distribution, MULTINODE_CLUSTERS_CONFIGS[0].version, MULTINODE_CLUSTERS_CONFIGS[0].krb
          )
      ],
      [
          stageName: "TEST Fault Tolerance on ${MULTINODE_CLUSTERS_CONFIGS[0].nameNode}",
          target: "test-hadoop-fault-tolerance", 
          timeoutValue: 45,
          component: config.COMPONENT_ANY,
          additionalTestPackages: [
                  config.COMPONENT_PY,
                  config.COMPONENT_R
          ],
          customData: MULTINODE_CLUSTERS_CONFIGS[0], 
          pythonVersion: v.PYTHON.active,
          executionScript: 'h2o-3/scripts/jenkins/groovy/faultToleranceStage.groovy',
          image: config.getHadoopEdgeNodeImage(
                  MULTINODE_CLUSTERS_CONFIGS[0].distribution, MULTINODE_CLUSTERS_CONFIGS[0].version, MULTINODE_CLUSTERS_CONFIGS[0].krb
          )
      ]
  ]

  def XGB_STAGES = []
  for (String osName: config.getSupportedXGBEnvironments().keySet()) {
    final def xgbEnvs = config.getSupportedXGBEnvironments()[osName]
    xgbEnvs.each {xgbEnv ->
      final def stageDefinition = [
        stageName: "XGB on ${xgbEnv.name}", 
        target: "test-xgb-smoke-${xgbEnv.targetName}-jenkins",
        timeoutValue: 15, 
        component: config.COMPONENT_ANY,
        additionalTestPackages: [config.COMPONENT_JAVA], 
        pythonVersion: v.PYTHON.first,
        image: config.getXGBImageForEnvironment(osName, xgbEnv),
        nodeLabel: xgbEnv.nodeLabel
      ]
      if (xgbEnv.targetName == config.XGB_TARGET_GPU) {
        stageDefinition['customDockerArgs'] = ['--runtime=nvidia', '--pid=host']
      }
      XGB_STAGES += stageDefinition
    }
  }

  def COVERAGE_STAGES = [
    [
      stageName: 'h2o-algos Coverage', 
      target: 'coverage-junit-algos', 
      pythonVersion: v.PYTHON.legacy, 
      timeoutValue: 5 * 60,
      executionScript: 'h2o-3/scripts/jenkins/groovy/coverageStage.groovy',
      component: config.COMPONENT_JAVA, 
      archiveAdditionalFiles: ['build/reports/jacoco/*.exec'],
      additionalTestPackages: [config.COMPONENT_PY], 
      nodeLabel: "${config.getDefaultNodeLabel()} && (!micro || micro_21)"
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
        additionalTestPackage = config.COMPONENT_PY
        break
      case 'R':
        target = 'test-r-single-test'
        additionalTestPackage = config.COMPONENT_R
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
        stageName: "Test ${params.testPath.split('/').last()} #${(it + 1)}", 
        target: target, 
        timeoutValue: 25,
        component: config.COMPONENT_ANY, 
        additionalTestPackages: [additionalTestPackage],
        pythonVersion: params.singleTestPyVersion, 
        rVersion: params.singleTestRVersion
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
    if (modeCode >= MODE_NIGHTLY_REPEATED_CODE) {
      jobs += NIGHTLY_REPEATED_STAGES
    }
    if (modeCode >= MODE_NIGHTLY_CODE) {
      jobs += NIGHTLY_STAGES
    }
    if (modeCode >= MODE_NIGHTLY_REPEATED_CODE) {
      // in Nightly mode execute all jobs regardless whether smoke tests fail 
      executeInParallel(SMOKE_STAGES + jobs, pipelineContext)
    } else {
      executeInParallel(SMOKE_STAGES, pipelineContext)
      if (modeCode == MODE_PR_CODE) {
        jobs += METADATA_VALIDATION_STAGES
      }
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
          healthCheckSuppressed = c['healthCheckSuppressed']
          addToDockerGroup = c['addToDockerGroup']
        }
      }
    ]
  })
}

private void invokeStage(final pipelineContext, final body) {

  final int DEFAULT_TIMEOUT = 60
  final String DEFAULT_EXECUTION_SCRIPT = 'h2o-3/scripts/jenkins/groovy/defaultStage.groovy'
  final int HEALTH_CHECK_RETRIES = 5

  def buildConfig = pipelineContext.getBuildConfig()
  def v = buildConfig.VERSIONS
  def config = [:]

  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  config.stageDir = pipelineContext.getUtils().stageNameToDirName(config.stageName)

  config.pythonVersion = config.pythonVersion ?: v.PYTHON.active
  if (config.activatePythonEnv == null) {
    config.activatePythonEnv = true // activate default python for run.py unless disabled
  }
  config.rVersion = config.rVersion ?: v.R.first
  config.javaVersion = config.javaVersion ?: v.JAVA.first_LTS
  config.timeoutValue = config.timeoutValue ?: DEFAULT_TIMEOUT
  config.customDockerArgs = config.customDockerArgs ?: []
  if (config.hasJUnit == null) {
    config.hasJUnit = true
  }
  config.additionalTestPackages = config.additionalTestPackages ?: []
  config.nodeLabel = config.nodeLabel ?: buildConfig.getDefaultNodeLabel()
  config.executionScript = config.executionScript ?: DEFAULT_EXECUTION_SCRIPT
  config.makefilePath = config.makefilePath ?: buildConfig.MAKEFILE_PATH
  config.archiveAdditionalFiles = config.archiveAdditionalFiles ?: []
  config.excludeAdditionalFiles = config.excludeAdditionalFiles ?: []
  if (config.archiveFiles == null) {
    config.archiveFiles = true
  }

  if (config.installRPackage == null) {
      config.installRPackage = true
  }

  config.image = config.image ?: buildConfig.getStageImage(config)
  if (config.healthCheckSuppressed == null) {
    config.healthCheckSuppressed = false
  }
  if (config.healthCheckSuppressed) {
    echo "######### Healthcheck suppressed #########"
  }

  if (buildConfig.componentChanged(config.component)) {
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

                healthCheckPassed = config.healthCheckSuppressed || pipelineContext.getHealthChecker().checkHealth(this, env.NODE_NAME, config.image, buildConfig.DOCKER_REGISTRY, buildConfig)
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
      withCustomCommitStates(scm, buildConfig.H2O_OPS_TOKEN, config.stageName) {
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
