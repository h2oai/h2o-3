def call(final context, final String mode, final String commitMessage, final changes,
         final boolean ignoreChanges, final List<String> distributionsToBuild, final List<String> gradleOpts,
         final String xgbVersion, final String gradleVersion) {
  def buildConfig = new BuildConfig()
  buildConfig.initialize(context, mode, commitMessage, changes, ignoreChanges, distributionsToBuild, gradleOpts, xgbVersion, gradleVersion)
  return buildConfig
}

class BuildConfig {

  public static final String DOCKER_REGISTRY = 'harbor.h2o.ai'

  private static final String DEFAULT_IMAGE_NAME = 'dev-build-base'
  private static final String DEFAULT_HADOOP_IMAGE_NAME = 'dev-build-hadoop'
  private static final String DEFAULT_RELEASE_IMAGE_NAME = 'dev-release'

  public static final int DEFAULT_IMAGE_VERSION_TAG = 44
  public static final String AWSCLI_IMAGE = DOCKER_REGISTRY + '/opsh2oai/awscli'
  public static final String S3CMD_IMAGE = DOCKER_REGISTRY + '/opsh2oai/s3cmd'

  private static final String HADOOP_IMAGE_NAME_PREFIX = 'h2o-3-hadoop'
  private static final String HADOOP_IMAGE_VERSION_TAG = '84'

  private static final String K8S_TEST_IMAGE_VERSION_TAG = '4' // Change the new Docker image in the K8S test YAML templates as well

  public static final String XGB_TARGET_MINIMAL = 'minimal'
  public static final String XGB_TARGET_OMP = 'omp'
  public static final String XGB_TARGET_GPU = 'gpu'
  public static final String XGB_BUILD_TAG = '-b1'
  private Map supportedXGBEnvironments

  public static final String COMPONENT_PY = 'py'
  public static final String COMPONENT_R = 'r'
  public static final String COMPONENT_JS = 'js'
  public static final String COMPONENT_JAVA = 'java'
  // Use to indicate, that the stage is not component dependent such as MOJO Compatibility Test,
  // always run
  public static final String COMPONENT_ANY = 'any'
  public static final String COMPONENT_HADOOP = 'hadoop'
  public static final String COMPONENT_MAIN = 'main'
  public static final String COMPONENT_MINIMAL = 'minimal' 
  public static final String COMPONENT_STEAM = 'steam'
  public static final List<String> TEST_PACKAGES_COMPONENTS = [COMPONENT_PY, COMPONENT_R, COMPONENT_JS, COMPONENT_JAVA, COMPONENT_HADOOP, COMPONENT_MINIMAL, COMPONENT_STEAM, COMPONENT_MAIN]

  public static final String H2O_JAR_STASH_NAME = 'h2o-3-stash-jar'
  private static final String TEST_PACKAGE_STASH_NAME_PREFIX = 'h2o-3-stash'

  public static final String H2O_OPS_TOKEN = 'h2o-ops-personal-auth-token'
  public static final String H2O_OPS_CREDS_ID = 'h2o-ops-gh-2020'
  private static final String COMMIT_STATE_PREFIX = 'H2O-3 Pipeline'

  public static final String RELEASE_BRANCH_PREFIX = 'rel-'

  public static final String DEFAULT_PYTHON_VERSION = '3.6'
  public static final List PYTHON_VERSIONS = ['3.6', '3.7', '3.8', '3.9', '3.10', '3.11']
  public static final List R_VERSIONS = ['3.3.3', '3.4.1']

  public static final String MAKEFILE_PATH = 'scripts/jenkins/Makefile.jenkins'
  public static final String BENCHMARK_MAKEFILE_PATH = 'ml-benchmark/jenkins/Makefile.jenkins'

  private static final List<String> STASH_ALWAYS_COMPONENTS = [COMPONENT_R, COMPONENT_MINIMAL, COMPONENT_STEAM, COMPONENT_MAIN]

  private static final String JACOCO_GRADLE_OPT = 'jacocoCoverage'

  private String mode
  private String nodeLabel
  private String commitMessage
  private boolean buildHadoop
  private List hadoopDistributionsToBuild
  private NodeLabels nodeLabels
  private LinkedHashMap changesMap = [
    (COMPONENT_PY): false,
    (COMPONENT_R): false,
    (COMPONENT_JS): false,
    (COMPONENT_JAVA): false,
    (COMPONENT_ANY): true
  ]
  private List<String> additionalGradleOpts
  private String xgbVersion
  private String gradleVersion
  private List<String> changedPythonTests

  void initialize(final context, final String mode, final String commitMessage, final changes,
                  final boolean ignoreChanges, final List<String> distributionsToBuild, final List<String> gradleOpts,
                  final String xgbVersion, final String gradleVersion) {
    this.mode = mode
    this.nodeLabel = nodeLabel
    this.commitMessage = commitMessage
    this.buildHadoop = (mode == 'MODE_HADOOP' || mode == 'MODE_KERBEROS')
    this.additionalGradleOpts = gradleOpts
    this.xgbVersion = xgbVersion
    this.gradleVersion = gradleVersion
    this.hadoopDistributionsToBuild = distributionsToBuild
    if (ignoreChanges) {
      markAllComponentsForTest()
    } else {
      detectChanges(changes)
    }
    changesMap[COMPONENT_HADOOP] = buildHadoop
    changedPythonTests = detectPythonTestChanges(changes)

    nodeLabels = NodeLabels.findByBuildURL(context.env.BUILD_URL)
    supportedXGBEnvironments = [
      'centos7.3': [
        [name: 'CentOS 7.3 Minimal', dockerfile: 'xgb/centos/Dockerfile-centos-minimal', fromImage: 'centos:7.3.1611', targetName: XGB_TARGET_MINIMAL, nodeLabel: getDefaultNodeLabel()],
        [name: 'CentOS 7.3 OMP', dockerfile: 'xgb/centos/Dockerfile-centos-omp', fromImage: "harbor.h2o.ai/opsh2oai/h2o-3-xgb-runtime-minimal:centos7.3${XGB_BUILD_TAG}", targetName: XGB_TARGET_OMP, nodeLabel: getDefaultNodeLabel()],
      ],
      'centos7.4': [
        [name: 'CentOS 7.4 GPU', dockerfile: 'xgb/centos/Dockerfile-centos-gpu', fromImage: 'nvidia/cuda:11.0.3-devel-centos7', targetName: XGB_TARGET_GPU, nodeLabel: getGPUNodeLabel()],
      ],
      'ubuntu16': [
        [name: 'Ubuntu 16.04 Minimal', dockerfile: 'xgb/ubuntu/Dockerfile-ubuntu-minimal', fromImage: 'ubuntu:16.04', targetName: XGB_TARGET_MINIMAL, nodeLabel: getDefaultNodeLabel()],
        [name: 'Ubuntu 16.04 OMP', dockerfile: 'xgb/ubuntu/Dockerfile-ubuntu-omp', fromImage: "harbor.h2o.ai/opsh2oai/h2o-3-xgb-runtime-minimal:ubuntu16${XGB_BUILD_TAG}", targetName: XGB_TARGET_OMP, nodeLabel: getDefaultNodeLabel()],
        [name: 'Ubuntu 16.04 GPU', dockerfile: 'xgb/ubuntu/Dockerfile-ubuntu-gpu', fromImage: 'nvidia/cuda:11.0.3-devel-ubuntu16.04', targetName: XGB_TARGET_GPU, nodeLabel: getGPUNodeLabel()],
      ],
      'ubuntu18': [
        [name: 'Ubuntu 18.04 Minimal', dockerfile: 'xgb/ubuntu/Dockerfile-ubuntu-minimal', fromImage: 'ubuntu:18.04', targetName: XGB_TARGET_MINIMAL, nodeLabel: getDefaultNodeLabel()],
        [name: 'Ubuntu 18.04 OMP', dockerfile: 'xgb/ubuntu/Dockerfile-ubuntu-omp', fromImage: "harbor.h2o.ai/opsh2oai/h2o-3-xgb-runtime-minimal:ubuntu18${XGB_BUILD_TAG}", targetName: XGB_TARGET_OMP, nodeLabel: getDefaultNodeLabel()],
        [name: 'Ubuntu 18.04 GPU', dockerfile: 'xgb/ubuntu/Dockerfile-ubuntu-gpu', fromImage: 'nvidia/cuda:11.0.3-devel-ubuntu18.04', targetName: XGB_TARGET_GPU, nodeLabel: getGPUNodeLabel()],
      ]
    ]
  }

  def getMode() {
    return mode
  }

  def componentChanged(final String component) {
    return changesMap[component]
  }

  def stashComponent(final String component) {
    return componentChanged(component) || STASH_ALWAYS_COMPONENTS.contains(component)
  }

  String getDefaultNodeLabel() {
    return nodeLabels.getDefaultNodeLabel()
  }

  String getBenchmarkNodeLabel() {
    return nodeLabels.getBenchmarkNodeLabel()
  }

  String getGPUBenchmarkNodeLabel() {
    return nodeLabels.getGPUBenchmarkNodeLabel()
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
      "BUILD_HADOOP=false"
    ]
  }

  List<String> getChangedPythonTests() {
    return changedPythonTests
  }

  void setJobProperties(final context) {
    setJobProperties(context, null)
  }

  void setJobProperties(final context, final customProperties) {
    def jobProperties = [
      context.buildDiscarder(context.logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '25'))
    ]

    jobProperties += context.parameters([
      context.booleanParam(name: 'executeFailedOnly', defaultValue: false, description: 'If checked, execute only failed stages')
    ])
    
    if (customProperties != null) {
      jobProperties += customProperties
    }
    context.properties(
      jobProperties
    )
  }

  int getDefaultImageVersion() {
    return DEFAULT_IMAGE_VERSION_TAG
  }

  String getDefaultImage() {
    if (buildHadoop) {
      return getHadoopBuildImage()
    }
    return "${DOCKER_REGISTRY}/opsh2oai/h2o-3/${DEFAULT_IMAGE_NAME}:${DEFAULT_IMAGE_VERSION_TAG}"
  }

  String getHadoopBuildImage() {
    return "${DOCKER_REGISTRY}/opsh2oai/h2o-3/${DEFAULT_HADOOP_IMAGE_NAME}:${DEFAULT_IMAGE_VERSION_TAG}"
  }

  String getReleaseImage() {
    return "${DOCKER_REGISTRY}/opsh2oai/h2o-3/${DEFAULT_RELEASE_IMAGE_NAME}:${DEFAULT_IMAGE_VERSION_TAG}"
  }

  String getHadoopImageVersion() {
    return HADOOP_IMAGE_VERSION_TAG
  }

  String getK8STestImageVersion() {
    return K8S_TEST_IMAGE_VERSION_TAG
  }

  Map getSupportedXGBEnvironments() {
    return supportedXGBEnvironments
  }

  String getXGBImageForEnvironment(final String osName, final xgbEnv, final String buildTag) {
    def suffix = ""
    if (buildTag) {
      suffix = '-b' + buildTag.trim()
    }
    return "harbor.h2o.ai/opsh2oai/h2o-3-xgb-runtime-${xgbEnv.targetName}:${osName}${suffix}"
  }

  String getStageImage(final stageConfig) {
    if (stageConfig.imageSpecifier && stageConfig.imageVersion)
      return getDevImageReference(stageConfig.imageSpecifier, stageConfig.imageVersion)
    if (stageConfig.imageSpecifier)
      return getDevImageReference(stageConfig.imageSpecifier)
    def component = stageConfig.component
    if (component == COMPONENT_ANY) {
      if (stageConfig.additionalTestPackages.contains(COMPONENT_PY)) {
        component = COMPONENT_PY
      } else if (stageConfig.additionalTestPackages.contains(COMPONENT_R)) {
        component = COMPONENT_R
      } else if (stageConfig.additionalTestPackages.contains(COMPONENT_JAVA)) {
        component = COMPONENT_JAVA
      }
    }
    def imageComponentName
    def version
    switch (component) {
      case COMPONENT_JAVA:
      case COMPONENT_ANY:
        imageComponentName = 'jdk'
        version = stageConfig.javaVersion
        break
      case COMPONENT_PY:
        imageComponentName = 'python'
        version = stageConfig.pythonVersion
        break
      case COMPONENT_R:
        imageComponentName = 'r'
        version = stageConfig.rVersion
        break
      case COMPONENT_JS:
        imageComponentName = 'python'
        version = stageConfig.pythonVersion
        break
      default:
        throw new IllegalArgumentException("Cannot find image for component ${component}")
    }
    def imageVersion = DEFAULT_IMAGE_VERSION_TAG
    if (stageConfig.imageVersion)
      imageVersion = stageConfig.imageVersion
    return "${DOCKER_REGISTRY}/opsh2oai/h2o-3/dev-${imageComponentName}-${version}:${imageVersion}"
  }
  
  String getDevImageReference(final specifier, final version) {
    return "${DOCKER_REGISTRY}/opsh2oai/h2o-3/dev-${specifier}:${version}"
  }

  String getDevImageReference(final specifier) {
    return getDevImageReference(specifier, DEFAULT_IMAGE_VERSION_TAG)
  }  

  String getStashNameForTestPackage(final String platform) {
    return String.format("%s-%s", TEST_PACKAGE_STASH_NAME_PREFIX, platform == 'any' ? 'java' : platform)
  }

  List<String> getAdditionalGradleOpts() {
    return additionalGradleOpts
  }

  String getCurrentXGBVersion() {
    return xgbVersion
  }

  String getCurrentGradleVersion() {
    return gradleVersion
  }

  private void detectChanges(changes) {
    markAllComponentsForSkip()

    changesMap[COMPONENT_ANY] = true

    for (change in changes.keySet()) {
      if (change.startsWith('h2o-py/') || change == 'h2o-bindings/bin/gen_python.py') {
        changesMap[COMPONENT_PY] = true
      } else if (change.startsWith('h2o-r/') ||  change == 'h2o-bindings/bin/gen_R.py') {
        changesMap[COMPONENT_R] = true
      } else if (change.endsWith('.md') || change.endsWith('.rst')) {
        // no need to run any tests if only .md/.rst files are changed
      } else {
        markAllComponentsForTest()
      }
    }
  }

  private static List<String> detectPythonTestChanges(changes) {
    changes.findAll { changeEntry ->
      def changeType = changeEntry.value
      def change = changeEntry.key
      changeType != "D" && // was not deleted
              change.startsWith('h2o-py/') && change.contains("pyunit_") &&
              change.lastIndexOf("pyunit_") > change.lastIndexOf("/") && // Allow to run "pyunit_*" files inside of "pyunit_*" directory but do not allow e.g. utilsPY.py to run
              !change.contains("pyunit_explain") && !change.endsWith("_large.py") && // takes too much time to run
              !change.contains("_NOPASS.py") &&  // We know these test fail often and randomly, no need to run it in this stage
              !change.contains("pyunit_h2oassembly_download_mojo") &&
              !change.contains("test_cloud") &&
              !change.contains("demos")
    }.collect {
      it.key.replaceFirst(".*pyunit_", "pyunit_") // Extract only filename from path
    }
  }

  private void markAllComponentsForTest() {
    changesMap.each { k,v ->
      changesMap[k] = true
    }
    changesMap[COMPONENT_HADOOP] = buildHadoop
  }

  private void markAllComponentsForSkip() {
    changesMap.each { k,v ->
      changesMap[k] = k == COMPONENT_ANY
    }
  }

  GString getGitHubCommitStateContext(final String stageName) {
    return "${COMMIT_STATE_PREFIX} » ${stageName}"
  }

  String getSmokeHadoopImage(final distribution, final version, final useKRB) {
    def krbSuffix = ''
    if (useKRB) {
        krbSuffix = '-krb'
    }
    return getSmokeHadoopImageImpl(distribution, version, krbSuffix)
  }

  String getHadoopEdgeNodeImage(final distribution, final version, final useKrb = true) {
    def suffix = "-0xd-edge"
    if (useKrb) {
      suffix = '-krb' + suffix
    }
    return getSmokeHadoopImageImpl(distribution, version, suffix)
  }

  String getSmokeHadoopImageImpl(final distribution, final version, final suffix) {
    return "${DOCKER_REGISTRY}/opsh2oai/${HADOOP_IMAGE_NAME_PREFIX}-${distribution}-${version}${suffix}:${HADOOP_IMAGE_VERSION_TAG}".toString()
  }

  static enum NodeLabels {
    LABELS_C1('docker && !mr-0xc8', 'mr-0xc9', 'gpu && !2gpu', 'mr-0xk10'), //master or nightly build
    LABELS_B4('docker', 'docker', 'gpu && !2gpu', 'docker')  //PR build

    static Map<String, NodeLabels> LABELS_MAP = [
            "c1": LABELS_C1,
            "g1": LABELS_C1, //mr-0xg1 was set as alias to mr-0xc1
            "b4": LABELS_B4
    ]

    private final String defaultNodeLabel
    private final String benchmarkNodeLabel
    private final String gpuNodeLabel
    private final String gpuBenchmarkNodeLabel

    private NodeLabels(final String defaultNodeLabel, final String benchmarkNodeLabel, final String gpuNodeLabel, final String gpuBenchmarkNodeLabel) {
      this.defaultNodeLabel = defaultNodeLabel
      this.benchmarkNodeLabel = benchmarkNodeLabel
      this.gpuNodeLabel = gpuNodeLabel
      this.gpuBenchmarkNodeLabel = gpuBenchmarkNodeLabel
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

    String getGPUBenchmarkNodeLabel() {
      return gpuBenchmarkNodeLabel
    }

    private static NodeLabels findByBuildURL(final String buildURL) {
      final String name = buildURL.replaceAll('http://mr-0x', '').replaceAll(':8080.*', '')

      if (LABELS_MAP.containsKey(name)) {
        return LABELS_MAP.get(name)
      } else {
        throw new IllegalArgumentException(String.format("Master %s (%s) is unknown", name, buildURL))
      }
    }
  }
}

return this
