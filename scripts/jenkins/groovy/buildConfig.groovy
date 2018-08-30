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
  public static final String AWSCLI_IMAGE = DOCKER_REGISTRY + '/awscli'

  private static final String BENCHMARK_IMAGE_NAME = 'h2o-3-benchmark'
  private static final String BENCHMARK_IMAGE_VERSION_TAG = 'latest'
  // Use this image for benchmark stages
  public static final String BENCHMARK_IMAGE = DOCKER_REGISTRY + '/opsh2oai/' + BENCHMARK_IMAGE_NAME + ':' + BENCHMARK_IMAGE_VERSION_TAG

  private static final String HADOOP_IMAGE_NAME_PREFIX = 'h2o-3-hadoop'
  private static final String HADOOP_IMAGE_VERSION_TAG = '49'

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

          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.2:49":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.2@sha256:9e2187b505df2055e148f89083a698c4542afbf1f9db6afa9abeccbd05a66088",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.2-krb:49": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.2-krb@sha256:d8820fd87deea14b0ae14e3b44f176de47efc1347c4301a2adb3799a85e762b4",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.3:49":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.3@sha256:39a47cc8302768211f9363545ae7cf6e6a0c09ec1b49a697a0036141c20212e8",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.3-krb:49": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.3-krb@sha256:111427b7e5ede1166ed1239954b90ea622a2761fd12e3cd4ed188c7223552da0",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.4:49":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.4@sha256:36857f05d61109c46c9f8c30d158b1dece5ba6d1fee9d8b88169201e7955aa7f",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.4-krb:49": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.4-krb@sha256:dbc55354ddfe38959b144a7f9209b8e34cead7f3898175ca5789c61ed12349e7",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.5:49":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.5@sha256:02bce647b48ab023509defac1ddbd5c6a44d3752670635eef9ace68fff8872eb",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.5-krb:49": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.5-krb@sha256:c354e68ad26c423925df1812aec45a2f59871f8151230e853618ca1be73a3287",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.6:49":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.6@sha256:26a9805399ce4fe60a55561d347a1fd197b0ffec0b3ee729decc39edf0401911",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.6-krb:49": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.6-krb@sha256:0c53081f04f12da10f3a81312c38dd455df4fdd8d8e0840cbc6862c54b74a433",

          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.4:49":      "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.4@sha256:0149837824fdbf5470f522711278d9b2e9ff5ad902447a55112e8518611f3e88",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.4-krb:49":  "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.4-krb@sha256:870af9691c2f43be8660c92b09d5febbd23660a0308ffea2c6cf64988a57c4c7",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.5:49":      "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.5@sha256:cc78af16c73b02848601dc3344202389054f82020c8b6ac386cf210b6b75ae12",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.5-krb:49":  "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.5-krb@sha256:35a54099466671d4f4ec75a9722c976728da98bed8124e10b2ca98584aaf2aab",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.6:49":      "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.6@sha256:0e3aaebe880ee6ec9fc72b03430b7d8a583ee26058f91ae6b077fe777b29765a",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.6-krb:49":  "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.6-krb@sha256:c5346a8e1736a56429da4c1f5c55f487febdfa12288681453abdc510bb5e62e2",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.7:49":      "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.7@sha256:e10e12ec1cd6a5874b820775c74a86119356fca6b850d31e2ee00789ad585f87",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.7-krb:49":  "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.7-krb@sha256:337600f59347cb435a81a58386671b2269c0db3bae7bdb2e6cf9f9eed551274c",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.8:49":      "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.8@sha256:affdbd1991480e817010377e5a2ab53ff9cca47a991eb164a973eeb5e5a34953",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.8-krb:49":  "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.8-krb@sha256:c00eb51fe52cbb529707ed052eb589dfdad6c0be23570398663aea12526c3eca",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.9:49":      "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.9@sha256:be5803046d7cdbe0d80bd29c5ee3cba53b3923ff3d0493b04ad60c222756baf6",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.9-krb:49":  "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.9-krb@sha256:59e90065df1ad4ef365b93fe8d68539c6d4b416be5cb6a79af218ef5b9f6ebfe",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.10:49":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.10@sha256:22d606a855af735f14c17cf0eb892a6913de99da6a36db7dfdaa5d5810934a03",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.10-krb:49": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.10-krb@sha256:1b33d830cd77919e9801ffe7983ddfe6632b9b71d070bbde2c7b59da933182bf",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.13:49":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.13@sha256:add58b2dbd0ded5a1b90ea2832eac1769e3816a4d80d00e8170faf04bfe6aaa9",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.13-krb:49": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.13-krb@sha256:6eb3b15795708f3ecf29748364c15cf302f9cb6f07e9fd9928dee948fc91dd04",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.14:49":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.14@sha256:ad3e24aead3518acfa76319d576bda06b4680318c7cc73fa0f0ca8127f9c8540",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.14-krb:49": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.14-krb@sha256:f93f20197f4c5bee33daa2b578cba2fd9070ff0a9dade5b20ced24a475c96471",

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
    return EXPECTED_IMAGE_VERSIONS[image]
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
