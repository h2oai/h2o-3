def call(final context, final String mode, final String commitMessage, final List<String> changes, final boolean ignoreChanges, final List<String> distributionsToBuild) {
  def buildConfig = new BuildConfig()
  buildConfig.initialize(context, mode, commitMessage, changes, ignoreChanges, distributionsToBuild)
  return buildConfig
}

class BuildConfig {

  public static final String DOCKER_REGISTRY = 'docker.h2o.ai'

  private static final String DEFAULT_IMAGE_NAME = 'h2o-3-runtime'
  private static final String DEFAULT_IMAGE_VERSION_TAG = '107'
  // This is the default image used for tests, build, etc.
  public static final String DEFAULT_IMAGE = DOCKER_REGISTRY + '/opsh2oai/' + DEFAULT_IMAGE_NAME + ':' + DEFAULT_IMAGE_VERSION_TAG

  private static final String BENCHMARK_IMAGE_NAME = 'h2o-3-benchmark'
  private static final String BENCHMARK_IMAGE_VERSION_TAG = '117'
  // Use this image for benchmark stages
  public static final String BENCHMARK_IMAGE = DOCKER_REGISTRY + '/opsh2oai/' + BENCHMARK_IMAGE_NAME + ':' + BENCHMARK_IMAGE_VERSION_TAG

  private static final String HADOOP_IMAGE_NAME_PREFIX = 'h2o-3-hadoop'
  private static final String HADOOP_IMAGE_VERSION_TAG = '46'

  private static final String XGB_IMAGE_VERSION_TAG = 'latest'
  public static final String XGB_TARGET_MINIMAL = 'minimal'
  public static final String XGB_TARGET_OMP = 'omp'
  public static final String XGB_TARGET_GPU = 'gpu'
  private Map supportedXGBEnvironments

  public static final String COMPONENT_PY = 'py'
  public static final String COMPONENT_R = 'r'
  public static final String COMPONENT_JS = 'js'
  public static final String COMPONENT_JAVA = 'java'
  // Use to indicate, that the stage is not component dependent such as MOJO Compatibility Test,
  // always run
  public static final String COMPONENT_ANY = 'any'
  public static final String COMPONENT_HADOOP = 'hadoop'

  public static final String H2O_OPS_TOKEN = 'h2o-ops-personal-auth-token'
  private static final String COMMIT_STATE_PREFIX = 'H2O-3 Pipeline'

  public static final String RELEASE_BRANCH_PREFIX = 'rel-'

  public static final List PYTHON_VERSIONS = ['2.7', '3.5', '3.6']
  public static final List R_VERSIONS = ['3.3.3', '3.4.1']

  public static final String MAKEFILE_PATH = 'scripts/jenkins/Makefile.jenkins'
  public static final String BENCHMARK_MAKEFILE_PATH = 'ml-benchmark/jenkins/Makefile.jenkins'

  private String mode
  private String nodeLabel
  private String commitMessage
  private boolean buildHadoop
  private List hadoopDistributionsToBuild
  private JenkinsMaster master
  private NodeLabels nodeLabels
  private LinkedHashMap changesMap = [
    (COMPONENT_PY): false,
    (COMPONENT_R): false,
    (COMPONENT_JS): false,
    (COMPONENT_JAVA): false,
    (COMPONENT_ANY): true
  ]

  void initialize(final context, final String mode, final String commitMessage, final List<String> changes,
                  final boolean ignoreChanges, final List<String> distributionsToBuild) {
    this.mode = mode
    this.nodeLabel = nodeLabel
    this.commitMessage = commitMessage
    this.buildHadoop = mode == 'MODE_HADOOP'
    this.hadoopDistributionsToBuild = distributionsToBuild
    if (ignoreChanges) {
      markAllComponentsForTest()
    } else {
      detectChanges(changes)
    }
    master = JenkinsMaster.findByBuildURL(context.env.BUILD_URL)
    nodeLabels = NodeLabels.findByJenkinsMaster(master)
    supportedXGBEnvironments = [
      'centos6.5': [
        [name: 'CentOS 6.5 Minimal', dockerfile: 'xgb/centos/Dockerfile-centos-minimal', fromImage: 'gpmidi/centos-6.5', targetName: XGB_TARGET_MINIMAL, nodeLabel: getDefaultNodeLabel(), runAfterMerge: true],
        [name: 'CentOS 6.5 OMP', dockerfile: 'xgb/centos/Dockerfile-centos-omp', fromImage: 'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-minimal:centos6.5', targetName: XGB_TARGET_OMP, nodeLabel: getDefaultNodeLabel(), runAfterMerge: true],
      ],
      'centos6.8': [
        [name: 'CentOS 6.8 Minimal', dockerfile: 'xgb/centos/Dockerfile-centos-minimal', fromImage: 'centos:6.8', targetName: XGB_TARGET_MINIMAL, nodeLabel: getDefaultNodeLabel()],
        [name: 'CentOS 6.8 OMP', dockerfile: 'xgb/centos/Dockerfile-centos-omp', fromImage: 'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-minimal:centos6.8', targetName: XGB_TARGET_OMP, nodeLabel: getDefaultNodeLabel()],
      ],
      'centos6.9': [
        [name: 'CentOS 6.9 GPU', dockerfile: 'xgb/centos/Dockerfile-centos-gpu', fromImage: 'nvidia/cuda:8.0-devel-centos6', targetName: XGB_TARGET_GPU, nodeLabel: getDefaultNodeLabel(), runAfterMerge: true],
      ],
      'centos7.3': [
        [name: 'CentOS 7.3 Minimal', dockerfile: 'xgb/centos/Dockerfile-centos-minimal', fromImage: 'centos:7.3.1611', targetName: XGB_TARGET_MINIMAL, nodeLabel: getDefaultNodeLabel()],
        [name: 'CentOS 7.3 OMP', dockerfile: 'xgb/centos/Dockerfile-centos-omp', fromImage: 'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-minimal:centos7.3', targetName: XGB_TARGET_OMP, nodeLabel: getDefaultNodeLabel()],
      ],
      'centos7.4': [
        [name: 'CentOS 7.4 GPU', dockerfile: 'xgb/centos/Dockerfile-centos-gpu', fromImage: 'nvidia/cuda:8.0-devel-centos7', targetName: XGB_TARGET_GPU, nodeLabel: getDefaultNodeLabel()],
      ],
      'ubuntu14': [
        [name: 'Ubuntu 14.04 Minimal', dockerfile: 'xgb/ubuntu/Dockerfile-ubuntu-minimal', fromImage: 'ubuntu:14.04', targetName: XGB_TARGET_MINIMAL, nodeLabel: getDefaultNodeLabel(), runAfterMerge: true],
        [name: 'Ubuntu 14.04 OMP', dockerfile: 'xgb/ubuntu/Dockerfile-ubuntu-omp', fromImage: 'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-minimal:ubuntu14', targetName: XGB_TARGET_OMP, nodeLabel: getDefaultNodeLabel(), runAfterMerge: true],
        [name: 'Ubuntu 14.04 GPU', dockerfile: 'xgb/ubuntu/Dockerfile-ubuntu-gpu', fromImage: 'nvidia/cuda:8.0-devel-ubuntu14.04', targetName: XGB_TARGET_GPU, nodeLabel: getGPUNodeLabel(), runAfterMerge: true],
      ],
      'ubuntu16': [
        [name: 'Ubuntu 16.04 Minimal', dockerfile: 'xgb/ubuntu/Dockerfile-ubuntu-minimal', fromImage: 'ubuntu:16.04', targetName: XGB_TARGET_MINIMAL, nodeLabel: getDefaultNodeLabel()],
        [name: 'Ubuntu 16.04 OMP', dockerfile: 'xgb/ubuntu/Dockerfile-ubuntu-omp', fromImage: 'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-minimal:ubuntu16', targetName: XGB_TARGET_OMP, nodeLabel: getDefaultNodeLabel()],
        [name: 'Ubuntu 16.04 GPU', dockerfile: 'xgb/ubuntu/Dockerfile-ubuntu-gpu', fromImage: 'nvidia/cuda:8.0-devel-ubuntu16.04', targetName: XGB_TARGET_GPU, nodeLabel: getGPUNodeLabel()],
      ]
    ]
  }

  def getMode() {
    return mode
  }

  def componentChanged(final String component) {
    return changesMap[component]
  }

  String getDefaultNodeLabel() {
    return nodeLabels.getDefaultNodeLabel()
  }

  String getBenchmarkNodeLabel() {
    return nodeLabels.getBenchmarkNodeLabel()
  }

  String getGPUNodeLabel() {
    return nodeLabels.getGPUNodeLabel()
  }

  boolean getBuildHadoop() {
    return buildHadoop
  }

  List getSupportedHadoopDistributions() {
    return hadoopDistributionsToBuild
  }

  List<String> getBuildEnv() {
    return [
      'JAVA_VERSION=8',
      "BUILD_HADOOP=${buildHadoop}",
    ]
  }

  void setJobProperties(final context) {
    setJobProperties(context, null)
  }

  void setJobProperties(final context, final customProperties) {
    def jobProperties = [
      context.buildDiscarder(context.logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '25'))
    ]
    if (context.env.BRANCH_NAME.startsWith('PR')) {
      jobProperties += context.parameters([
        context.booleanParam(name: 'executeFailedOnly', defaultValue: false, description: 'If checked, execute only failed stages')
      ])
    }
    if (customProperties != null) {
      jobProperties += customProperties
    }
    context.properties(
      jobProperties
    )
  }

  String getDefaultImageVersion() {
    return DEFAULT_IMAGE_VERSION_TAG
  }

  String getHadoopImageVersion() {
    return HADOOP_IMAGE_VERSION_TAG
  }

  String getXGBImageVersion() {
    return XGB_IMAGE_VERSION_TAG
  }

  Map getSupportedXGBEnvironments() {
    return supportedXGBEnvironments
  }

  String getXGBImageForEnvironment(final String osName, final xgbEnv) {
    return "docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-${xgbEnv.targetName}:${osName}"
  }

  String getXGBNodeLabelForEnvironment(final Map xgbEnv) {
    switch (xgbEnv.targetName) {
      case XGB_TARGET_GPU:
        return nodeLabels.getGPUNodeLabel()
      case XGB_TARGET_OMP:
        return nodeLabels.getDefaultNodeLabel()
      case XGB_TARGET_MINIMAL:
        return nodeLabels.getDefaultNodeLabel()
      default:
        throw new IllegalArgumentException("xgbEnv.targetName ${xgbEnv.targetName} not supported")
    }
  }

  private void detectChanges(List<String> changes) {
    markAllComponentsForSkip()

    changesMap[COMPONENT_ANY] = true

    for (change in changes) {
      if (change.startsWith('h2o-py/') || change == 'h2o-bindings/bin/gen_python.py') {
        changesMap[COMPONENT_PY] = true
      } else if (change.startsWith('h2o-r/') ||  change == 'h2o-bindings/bin/gen_R.py') {
        changesMap[COMPONENT_R] = true
      } else if (change.endsWith('.md')) {
        // no need to run any tests if only .md files are changed
      } else {
        markAllComponentsForTest()
      }
    }
  }

  private void markAllComponentsForTest() {
    changesMap.each { k,v ->
      changesMap[k] = true
    }
  }

  private void markAllComponentsForSkip() {
    changesMap.each { k,v ->
      changesMap[k] = k == COMPONENT_ANY
    }
  }

  GString getGitHubCommitStateContext(final String stageName) {
    return "${COMMIT_STATE_PREFIX} » ${stageName}"
  }

  String getSmokeHadoopImage(final String distribution, final String version) {
      return "${DOCKER_REGISTRY}/opsh2oai/${HADOOP_IMAGE_NAME_PREFIX}-${distribution}-${version}:${HADOOP_IMAGE_VERSION_TAG}"
  }

  static enum JenkinsMaster {
    C1, // indicates we are running under mr-0xc1 master - master or nightly build
    B4  // indicates we are running under mr-0xb4 master - PR build

    private static JenkinsMaster findByName(final String name) {
      switch(name.toLowerCase()) {
        case 'c1':
          return C1
        case 'b4':
          return B4
        default:
          throw new IllegalArgumentException(String.format("Master %s is unknown", name))
      }
    }

    private static JenkinsMaster findByBuildURL(final String buildURL) {
      final String name = buildURL.replaceAll('http://mr-0x', '').replaceAll(':8080.*', '')
      return findByName(name)
    }
  }

  static enum NodeLabels {
    LABELS_C1('docker && !mr-0xc8', 'mr-0xc9', 'mr-dl16'),
    LABELS_B4('docker', 'docker', 'mr-dl16')

    private final String defaultNodeLabel
    private final String benchmarkNodeLabel
    private final String gpuNodeLabel

    private NodeLabels(final String defaultNodeLabel, final String benchmarkNodeLabel, final String gpuNodeLabel) {
      this.defaultNodeLabel = defaultNodeLabel
      this.benchmarkNodeLabel = benchmarkNodeLabel
      this.gpuNodeLabel = gpuNodeLabel
    }

    String getDefaultNodeLabel() {
      return defaultNodeLabel
    }

    String getBenchmarkNodeLabel() {
      return benchmarkNodeLabel
    }

    String getGPUNodeLabel() {
      return gpuNodeLabel
    }

    private static findByJenkinsMaster(final JenkinsMaster master) {
      switch (master) {
        case JenkinsMaster.C1:
          return LABELS_C1
        case JenkinsMaster.B4:
          return LABELS_B4
        default:
          throw new IllegalArgumentException(String.format("Master %s is unknown", master))
      }
    }
  }

}

return this
