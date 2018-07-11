def call(final context, final String mode, final String commitMessage, final List<String> changes, final boolean ignoreChanges, final List<String> distributionsToBuild, final List<String> gradleOpts, final String xgbVersion) {
  def buildConfig = new BuildConfig()
  buildConfig.initialize(context, mode, commitMessage, changes, ignoreChanges, distributionsToBuild, gradleOpts, xgbVersion)
  return buildConfig
}

class BuildConfig {

  public static final String DOCKER_REGISTRY = 'docker.h2o.ai'

  private static final String DEFAULT_IMAGE_NAME = 'h2o-3-runtime'
  private static final String DEFAULT_IMAGE_VERSION_TAG = '111'
  // This is the default image used for tests, build, etc.
  public static final String DEFAULT_IMAGE = DOCKER_REGISTRY + '/opsh2oai/' + DEFAULT_IMAGE_NAME + ':' + DEFAULT_IMAGE_VERSION_TAG

  private static final String BENCHMARK_IMAGE_NAME = 'h2o-3-benchmark'
  private static final String BENCHMARK_IMAGE_VERSION_TAG = 'latest'
  // Use this image for benchmark stages
  public static final String BENCHMARK_IMAGE = DOCKER_REGISTRY + '/opsh2oai/' + BENCHMARK_IMAGE_NAME + ':' + BENCHMARK_IMAGE_VERSION_TAG

  private static final String HADOOP_IMAGE_NAME_PREFIX = 'h2o-3-hadoop'
  private static final String HADOOP_IMAGE_VERSION_TAG = '48'

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
  public static final List<String> TEST_PACKAGES_COMPONENTS = [COMPONENT_PY, COMPONENT_R, COMPONENT_JS, COMPONENT_JAVA, COMPONENT_HADOOP]

  public static final String H2O_JAR_STASH_NAME = 'h2o-3-stash-jar'
  private static final String TEST_PACKAGE_STASH_NAME_PREFIX = 'h2o-3-stash'

  public static final String H2O_OPS_TOKEN = 'h2o-ops-personal-auth-token'
  private static final String COMMIT_STATE_PREFIX = 'H2O-3 Pipeline'

  public static final String RELEASE_BRANCH_PREFIX = 'rel-'

  public static final List PYTHON_VERSIONS = ['2.7', '3.5', '3.6']
  public static final List R_VERSIONS = ['3.3.3', '3.4.1']

  public static final String MAKEFILE_PATH = 'scripts/jenkins/Makefile.jenkins'
  public static final String BENCHMARK_MAKEFILE_PATH = 'ml-benchmark/jenkins/Makefile.jenkins'

  private static final Map EXPECTED_IMAGE_VERSIONS= [
          (DEFAULT_IMAGE): 'docker.h2o.ai/opsh2oai/h2o-3-runtime@sha256:21a6e898ac8a5facf96fd54e53d0ccb9e13f887ba18deabd2504a962ad2a1a95',
          (BENCHMARK_IMAGE): 'docker.h2o.ai/opsh2oai/h2o-3-benchmark@sha256:1edd212621bb8a9e6469c450a86c5136cecdc5d5371179a6e9b513b032dbc182',

          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.2:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.2@sha256:ea4aa3ad81d8cff2063c92afa9f7297a1565f7e3cfc5a7a997fcd5bf5bf948b2",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.3:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.3@sha256:c59ccef9a5033cbcbc5953a5b5678528bd5e8c8adf64c75444b615be5183f4e7",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.4:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.4@sha256:755ee5ac48aca1d4a4d492a52a89808e1c126a2d9ee8b2bff2ebd7de364ecbf1",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.5:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.5@sha256:5b3136ba4b8ca031c2f265279ee045cf59adf64ef553237b29b50bf20ce95fc5",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.6:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.6@sha256:d63d80b7c5b1af676539c9ad7767f7b4e3a68ffb0a0167caf6131154ac961811",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.2-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.2-krb@sha256:39dfc3381fd849454d17dfddfee5fe5c83692fc43d7bc75959a6b6ca9b9dfe88",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.3-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.3-krb@sha256:317af586243f97a363ce2250ce26e2855e5ea5da8458b9ae79e992a3e9e01aae",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.4-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.4-krb@sha256:877fc4138a601bc54c6969906b5d38eb89b32d5e30cdcd3cc6c96d0cd35e91d4",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.5-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.5-krb@sha256:dd0ec4005e7aac4c888327d06ba7617aae010b8894061d508fe106a43e4a4ec1",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.6-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.6-krb@sha256:8f702bca7d09514a09e855b3d04292838f141a75ed8d920987711f13a579c78d",

          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.4:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.4@sha256:a85fb396d476e4e6140803ce72e47da96cd12036ebab493fd756264b46170a8e",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.5:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.5@sha256:b8450e8136b7f7bfdf6f1a93f0793184f6a49d1dfb0734709a4597bb5d7fd19f",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.6:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.6@sha256:af8209c055acc28062557099020d8bcbd455e7a0c290f9e663a95e3f915027ae",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.7:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.7@sha256:21e682f228d8ba57a3700b7c0b5721d9b5cdd954fb4717507b015873e7ff2494",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.8:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.8@sha256:c498a445b59dbd4e6878e4241184f2d9c9426a670b38235111936db79be3e65e",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.10:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.10@sha256:aa4cb86955bc58a28865f83cdbc52cca0fdfcd1951ff0b03c1b94ae9687df282",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.13:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.13@sha256:169123ea81a6adca4a59d8f96346a311ef68adaf563b4433c79c87b1fa547150",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.14:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.14@sha256:66eda61956eacecb03f7af6a012b9065d393045e88cbe5f94ad9c344724f6fff",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.4-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.4-krb@sha256:74d3e858c5f00c7e2dfb17e3513beeeed5d581a51fcf156e6523d03e3c73bd9f",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.5-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.5-krb@sha256:4cbc6b542b94f3316350771ffce7f887efc3766f4b20d69be0be6dbb5088bcc7",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.6-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.6-krb@sha256:a06ed995c60644e8c99b4782bf25c4327fdc38a7ef9bef3bab25b5502336f923",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.7-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.7-krb@sha256:d99d19e11d82e127e83bee88117236272085ced0dfc71c86ba4feaf9cea20e42",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.8-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.8-krb@sha256:6aa3ae67738d7d00ac0791c96d4e1103b8bc82f271b6f769a5d8939af90d55ed",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.10-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.10-krb@sha256:9c1a30ffaf726eabd8bd2ebe3ea6e36fa92e1c95ca52431c3ac72bc92f6580fb",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.13-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.13-krb@sha256:cc5a1c2c67129668e841c594833ef9787e4c9fc7979adfd78d1bccbad3fe14dc",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.14-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.14-krb@sha256:5168ea1421bdbadeb9b014f4ff7af864fecf00fc6f56cc62293049d50e40d5d6",

          'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-minimal:ubuntu14': 'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-minimal@sha256:20ac938598eb03609652da53d8b654476f867864c3827cc91164832e3ff82822',
          'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-omp:ubuntu14': 'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-omp@sha256:50f762e48e479db73ebc611d8727537db32d13aeac3dc2b8eba10a5993f782dc',
          'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-gpu:ubuntu14': 'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-gpu@sha256:1ea7b697084e33353480507a0786219e4cae6d6cfdfc16a5d0ee8505777d1c7f',
          'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-minimal:ubuntu16': 'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-minimal@sha256:918806875e01e5b7e8babd875199a6177e2f1bebe6a2d3574ea964f8b4486f4e',
          'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-omp:ubuntu16': 'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-omp@sha256:3cd1dfac210157656cc22675d1a7f7e354e1d702e11e47c7cfc46c86138a83fb',
          'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-gpu:ubuntu16': 'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-gpu@sha256:3a8897cf2d819b4fb259f871e2a20ce0461fcca5cb94455e74b6ecba6f2acb3d',
          'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-minimal:centos6.5': 'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-minimal@sha256:8a488db041f801c5c26ab328c0026710203fb32a5bc02d43d665b5a313c465df',
          'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-omp:centos6.5': 'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-omp@sha256:c4df83c32d233c064e3936b01d122eaccb315cf42d6add6aa9a64ff5344c1b57',
          'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-minimal:centos6.8': 'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-minimal@sha256:f680ad80523d2ed2cd9076d27f7bfdb7293f8163def3fa506cd20f8a27f43aa9',
          'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-omp:centos6.8': 'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-omp@sha256:b4e677233ec1f948708391ad26f9138f632b5ecd61657937d220c2265391bfc7',
          'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-gpu:centos6.9': 'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-gpu@sha256:e606322a1c2849f110ff5da88161f459586266b07ace1fd4e0db99b5095ee5b3',
          'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-minimal:centos7.3': 'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-minimal@sha256:e465621bee73fa4792019e56a08c8e6350a5935906dae37a22ea1ccbdf0ae210',
          'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-omp:centos7.3': 'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-omp@sha256:d75d5ecd58f65dad36360acb93bdb359e9a581f15f50c477f0843f84006e7178',
          'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-gpu:centos7.4': 'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-gpu@sha256:9f06daa60598a972a922239427b1d241ae0a55b5174c2030c4ac3c2dffa2fe14',
  ]

  private static final List<String> STASH_ALWAYS_COMPONENTS = [COMPONENT_R]

  private static final String JACOCO_GRADLE_OPT = 'jacocoCoverage'

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
  private List<String> additionalGradleOpts
  private String xgbVersion

  void initialize(final context, final String mode, final String commitMessage, final List<String> changes,
                  final boolean ignoreChanges, final List<String> distributionsToBuild, final List<String> gradleOpts, final String xgbVersion) {
    this.mode = mode
    this.nodeLabel = nodeLabel
    this.commitMessage = commitMessage
    this.buildHadoop = mode == 'MODE_HADOOP'
    this.additionalGradleOpts = gradleOpts
    this.xgbVersion = xgbVersion
    this.hadoopDistributionsToBuild = distributionsToBuild
    if (ignoreChanges) {
      markAllComponentsForTest()
    } else {
      detectChanges(changes)
    }
    changesMap[COMPONENT_HADOOP] = buildHadoop

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

  def stashComponent(final String component) {
    return componentChanged(component) || STASH_ALWAYS_COMPONENTS.contains(component)
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

  String getExpectedImageVersion(final String image) {
    final def version = EXPECTED_IMAGE_VERSIONS[image]
    if (version == null) {
      throw new IllegalArgumentException(String.format('Cannot find expected image version for %s', image))
    }
    return version
  }

  String getStashNameForTestPackage(final String platform) {
    return String.format("%s-%s", TEST_PACKAGE_STASH_NAME_PREFIX, platform)
  }

  List<String> getAdditionalGradleOpts() {
    return additionalGradleOpts
  }

  String getCurrentXGBVersion() {
    return xgbVersion
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
      return "${DOCKER_REGISTRY}/opsh2oai/${HADOOP_IMAGE_NAME_PREFIX}-${distribution}-${version}${krbSuffix}:${HADOOP_IMAGE_VERSION_TAG}"
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
