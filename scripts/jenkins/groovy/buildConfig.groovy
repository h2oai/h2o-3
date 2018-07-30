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

          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.2:48":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.2@sha256:587f8a5eb710888efa9f40b183b18d70e49b84f364e1e55868805b45b6fd63e6",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.2-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.2-krb@sha256:701ed9ffcd7c2b5f982236d3e3fb2138e035c49cf49da4999f5ae1542c7ac56f",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.3:48":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.3@sha256:396d8b618d369ade27d2be7e7a567f3753e923c62b45f2b8a54464743759f40c",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.3-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.3-krb@sha256:03f5b814df2292b3aee72ac9711cb5440e70b1241fbf391e0d89c1bcc9d267de",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.4:48":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.4@sha256:fa09c475f12d579adfda48f5db05312098c3f57c9956d8ec168c02258b5dafc6",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.4-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.4-krb@sha256:c7bf87beecacf9e22a265a96b08a1191967472f97cd9492c7d9366b7e7398b07",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.5:48":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.5@sha256:7810ca19fc0cb96f3bbf88c461e7c5de6465f32a726157783d2f87dbcf7b2c84",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.5-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.5-krb@sha256:b52100dffd04e507956bf56a28a9d7ab4787fc7a3420b7d43733095d032a0d48",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.6:48":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.6@sha256:dc44919c0772a2a4e0beae10adb47b34c2b87927458e0a3b45fdc9b5ff1306f4",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.6-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.6-krb@sha256:9e4c0d9ba63e194d846bbc5bcf492436e8f36677eaa4000293e62d87f13c0088",

          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.4:48":      "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.4@sha256:fa603607618b2da5f30474255bcae3430590e7708d41dd22eaa0fdccafca231d",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.4-krb:48":  "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.4-krb@sha256:d8ff9472876968cf94f11db37a2d706fc422cb1a6fee056bab13af5cd6b47d94",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.5:48":      "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.5@sha256:eafe6589019b287eda329b69454e4c1c7472dcca3b3c7669f9730332d1570b23",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.5-krb:48":  "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.5-krb@sha256:501af21c91716871af77da9b5c668f2c343cbd8918c59e91696e40f017e1a404",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.6:48":      "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.6@sha256:179d4af30b0f79f2e435e393f7efef4468c91e18f8e9f710a4ddb8c4db89b8b8",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.6-krb:48":  "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.6-krb@sha256:5eeb7af58b25e4597e3263e7218123568024465ec89df029037ed60858056849",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.7:48":      "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.7@sha256:c4f8774751382ceb5a0acf832b9fcb7b5b9bc3c494bd4baab0af4106a436667a",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.7-krb:48":  "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.7-krb@sha256:87206a490e5f2f3263febab7b808e4357ad9e9966a530e3c38af5a4a646b6fc7",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.8:48":      "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.8@sha256:d97a2b2b11df3c72e8ba37d4d5ee62763b7f8ce5affc06f643543b186d7c7c62",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.8-krb:48":  "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.8-krb@sha256:bd07a02e88092cc325b4faefb1ad62f223fa5f1cd971f1d1236a314f3af10949",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.9:48":      "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.9@sha256:2d8416ffb624a58c19dacb4daaf95dce0a400208e4708fc20aeed96dd4d3b434",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.9-krb:48":  "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.9-krb@sha256:d8f27bd7b44a60691c1bb4bed7a7b10cb5493ef17e0f24388c10442fcf6a84d7",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.10:48":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.10@sha256:6918f9f4cb8e6a7d4641743cf1754449df79fec4aa59bb3ad84963e6769da939",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.10-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.10-krb@sha256:8e5e3140e9d965ff92a1609e7445c63999fb3eff7c1a8f133031d2362c5a129f",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.13:48":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.13@sha256:4342bba3252de5ddfa33332ff3c6986b103237abca20f409f6ad6d03ace8fa1d",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.13-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.13-krb@sha256:66aae795bcd1d8939bf0f1619280fa1f74e45c4d938c35f7365143c2f36e56f8",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.14:48":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.14@sha256:d2b7b6ed320b9784c38e616113e20c57de0f67d469cf161e23c5d8a77b678c37",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.14-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.14-krb@sha256:db59d21403b57714335286e37544fdded515623db1377a75b95f7a39a466f147",

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
