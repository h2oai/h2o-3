def call(final context, final String mode, final String commitMessage, final List<String> changes, final boolean ignoreChanges, final List<String> distributionsToBuild, final List<String> gradleOpts, final String xgbVersion) {
  def buildConfig = new BuildConfig()
  buildConfig.initialize(context, mode, commitMessage, changes, ignoreChanges, distributionsToBuild, gradleOpts, xgbVersion)
  return buildConfig
}

class BuildConfig {

  public static final String DOCKER_REGISTRY = 'docker.h2o.ai'

  private static final String DEFAULT_IMAGE_NAME = 'h2o-3-runtime'
  private static final String DEFAULT_IMAGE_VERSION_TAG = '113'
  // This is the default image used for tests, build, etc.
  public static final String DEFAULT_IMAGE = DOCKER_REGISTRY + '/opsh2oai/' + DEFAULT_IMAGE_NAME + ':' + DEFAULT_IMAGE_VERSION_TAG
  public static final String AWSCLI_IMAGE = DOCKER_REGISTRY + '/awscli'

  private static final String HADOOP_IMAGE_NAME_PREFIX = 'h2o-3-hadoop'
  private static final String HADOOP_IMAGE_VERSION_TAG = '50'

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
          (DEFAULT_IMAGE): 'docker.h2o.ai/opsh2oai/h2o-3-runtime@sha256:65dc665c83eb564903f662dcb74a1b9429f926c04e850a1e31fa5e5b51f1718a',

          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.2:50":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.2@sha256:8b56c76d07b95214ed59fa2301b14d921cf0aa589660fc320eabce0da2c3c301",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.2-krb:50": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.2-krb@sha256:d4ced9cbb0f62a726a89067c79dab3f9ad38ecf2c1d05b3ca3811a885673fac2",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.3:50":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.3@sha256:3208c8f8a74b8c0a1a0b207f0843129df25d8c54afa420e78d716e695825e9fd",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.3-krb:50": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.3-krb@sha256:f439eb7ab08ce28fc72735fbd8a4107a5bc4dd0238ffedc5b4bb4479b7f7cf5d",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.4:50":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.4@sha256:5dafcdc309de04e603ad14d470548bf562bb4d04ffdd735db0fa78188e85271a",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.4-krb:50": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.4-krb@sha256:378e9510b491db15fee72a678a2b9e4ed03d4ef220b067365e13c4ff96fc4b69",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.5:50":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.5@sha256:c1c6403021429037eb22cf4d2416ba3b97d10ab182ca0b760ce1f31760a06110",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.5-krb:50": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.5-krb@sha256:687aee554caf53439a459d028789352c270ca8a353a8961744a90f3477fb4ebc",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.6:50":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.6@sha256:01e292543d2d4b0ce6dc74c0d9ecc174be316f819ed328a05bf9ef7c5a7eca3d",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.6-krb:50": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.6-krb@sha256:3106a7fcfaa5ef498c8a640985ab335234590926a732070a1b6769400f59cfd4",

          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.4:50":      "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.4@sha256:6b6bd64a27634afa4911d45d4710a65ec48270d9c5d2393404ca10583366244e",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.4-krb:50":  "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.4-krb@sha256:f0db19806712d08722bac158def92e38028f030d91404ab6efd5d198fa9afcef",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.5:50":      "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.5@sha256:3e4f1931b6e391dbf43d608707437a43529f1fefb188c4daa707e6394202f425",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.5-krb:50":  "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.5-krb@sha256:758f8bc15e1087d7f02464fbb9e953c22206976756f074f140bf8ce7106fdc63",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.6:50":      "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.6@sha256:f07849e9d280c456ea4754262e180a5aece9b572836d1916daca642b4ea4e54c",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.6-krb:50":  "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.6-krb@sha256:4856516bf4adbdbf584bd65f15a9d8d9009c8302dd465c0095472802a633def9",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.7:50":      "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.7@sha256:07562f39e0d402dcd52b272a801ba4ad00739c821c2146018faf86c37c470caf",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.7-krb:50":  "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.7-krb@sha256:4c7814379982b71d311fe4b823aab507d525c70d45640cb84229a7554c9fcb6d",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.8:50":      "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.8@sha256:f04d20f02819b061e09f447a1b9905bc705f9e1f1d5a77b9f064bf6aa6f2bcbd",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.8-krb:50":  "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.8-krb@sha256:0dfad8ebc31c50c79e14284cb49094b8c8e58cd213841d826a97b74ec2e8e398",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.9:50":      "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.9@sha256:335c25f6a9c7171d4a5664cb7e752e82b2a10e3eeffdd64b1e357a286d15b74d",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.9-krb:50":  "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.9-krb@sha256:9310728875dd07da29f78c9a11247e5dfd86b907c6943ce44b9c8d8b71c820b7",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.10:50":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.10@sha256:d1b778de4517719d38bc2e5ddfded5db253dac499cb70e5b8f67d4f8668be885",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.10-krb:50": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.10-krb@sha256:830149b3b8ecb89d395d654e2ebe768c18c0c4378c81a422bebb4bc87ee3fa8a",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.13:50":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.13@sha256:0d3be1d933296891ad1e1f96efc07d0c25ab5c1913830cb7dffd654b95a19b7e",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.13-krb:50": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.13-krb@sha256:f6f4df614b673abd1b3d03c620154bed71f936aef683e7d32b0ae7ad868de974",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.14:50":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.14@sha256:04bd1276d1c5caaab86bab28eaea9dc2b0899bc3302577a6655af0f92919ebdd",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.14-krb:50": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.14-krb@sha256:3dd81025f0392b37581329f4d85b6f489d239387e8f3da3a849e1c4fe0549e5c",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-6.0:50":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-6.0@sha256:5ebf73eba208906e84fb6a23b4cc55263e5deb065b39ec7070f90c96a3c92390",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-6.0-krb:50": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-6.0-krb@sha256:a937ce41fad7c1ca9ec0af3832e996bafb367305d246a42a84ff32846c6ca1f9",

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
