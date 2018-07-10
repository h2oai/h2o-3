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

          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.2:48":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.2@sha256:eacd8b44fefae31e244f99da2968407f5f4aeaca7442fa289dea842234089c25",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.2-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.2-krb@sha256:e0d662aadcf7b4a4e2d018911e2566f38ff71621e5e9980f56eebe05e2b7c38e",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.3:48":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.3@sha256:ab7f09afd6a99033d8baf788c9c9beaa06b54b7c59863adc9c11600e6baca090",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.3-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.3-krb@sha256:653ea54eec81d9ac657099c916be73021f42c2a83cf1a2b2155494153f2403a8",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.4:48":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.4@sha256:eac380bec1f75a962e254911d20b3bae951ae4753f8b57237c9ec476ec9c8be3",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.4-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.4-krb@sha256:527ba299e033cf808073184b65d6713b9be58b142771edffbe979561b0483077",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.5:48":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.5@sha256:5b3136ba4b8ca031c2f265279ee045cf59adf64ef553237b29b50bf20ce95fc5",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.5-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.5-krb@sha256:dd0ec4005e7aac4c888327d06ba7617aae010b8894061d508fe106a43e4a4ec1",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.6:48":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.6@sha256:f150da610db01173c714cc798f893c4b0843822368669877071ba62b5e5ce064",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.6-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.6-krb@sha256:193947c1432113db921aa57cae7e3e59231f34da278cbd3513c35231d76ce425",

          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.4:48":      "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.4@sha256:e9393cccf6159f9a7a53410745d8d8f1445fc29c1e5c0dbabd248541687f8a2f",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.4-krb:48":  "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.4-krb@sha256:b6aeea6747ab7a99f2b3abdd50f1ecba2df10f4c24bc0ba6245ad6ed5a05c03d",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.5:48":      "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.5@sha256:0ffc2550708bad2dc23299728900dc30f21ed812472445a09e2858e53e206916",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.5-krb:48":  "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.5-krb@sha256:eef09cef01feb3e3bf0b39301925a765a8426697671d584223bddc6b66ff2a51",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.6:48":      "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.6@sha256:e15f8dcae8fef1b54ba326ecba52b47178679b5a45160c18a762366fdac7c80e",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.6-krb:48":  "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.6-krb@sha256:cf4a2d616ceb37397e8dce53b20742504e70be7d645dfd7aebc45ce4a5597883",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.7:48":      "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.7@sha256:005efc42576bc44df60d2be698e1a6c593610b1f12e39cdab8ca81f167af7bdf",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.7-krb:48":  "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.7-krb@sha256:b2e24abef9a4b384a04ad3886b35ad06c2d699c64f61faf6cdcf4b7750e6ec3a",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.8:48":      "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.8@sha256:16b065a98f0fd9345eb0fd93be8f3c574aea58ddaa81f3735070fd0be866d39c",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.8-krb:48":  "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.8-krb@sha256:c43a894f7178f1b6e0a5f866d1aa6062781921c4594c7e7f73a6284f0ee3f5cd",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.9:48":      "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.9@sha256:49feb4a547b3ce43160228375f5c0add7d1f9e08e6f92fec0cd24c50ea4ff7a1",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.9-krb:48":  "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.9-krb@sha256:65ee0ddc2d3fcecafd281c941d7a330062a693bdaee2c7c62abd5b2a1144d9a7",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.10:48":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.10@sha256:61b8260fb465f78b5a527ab28ec4c86b79bffaa4908c4082913f271163fb76a8",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.10-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.10-krb@sha256:f390641852aa0a9be83652d181cba8afb8750c0e521d226547417c642aad75d3",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.13:48":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.13@sha256:0869bad7b842c853e4112ab1b2697ab4a3eac073ef775675db40427567fc7b03",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.13-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.13-krb@sha256:c2adb58fc2c96b3be0323405511afbc6338203dba50942852a5e09eb3d0a2eee",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.14:48":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.14@sha256:84557dbedca111d93fa0cc8911c8bb7f88f43e212bb8f4f91c062c28204e473d",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.14-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.14-krb@sha256:7685c270dc211b21d6815531997b77be6c8baedc4d3114bcf4d46ba21f4b70a7",

          'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-minimal:ubuntu14': 'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-minimal@sha256:b77bb053dfce986b5a64c963031eaf611a3f7b1af4236a9df51ee7674d55f00e',
          'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-omp:ubuntu14': 'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-omp@sha256:245a62fae91d2ede4c0074f58b4c32ea980b701cb08f1af19dc51e9058d3880a',
          'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-gpu:ubuntu14': 'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-gpu@sha256:5f2cab2154dad9e3b61aa089ee1afea95189b8fdc797eca90c4a1ff5d6ebee2e',
          'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-minimal:ubuntu16': 'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-minimal@sha256:70f5cb64fa1f51846a61aca2f8612eb3af388d101b72030ca25e33cec22355bb',
          'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-omp:ubuntu16': 'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-omp@sha256:7ce669c80be206a125b4cd3cdd59fc6fc2df053acc0d98969d316165df67cfe2',
          'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-gpu:ubuntu16': 'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-gpu@sha256:218f2c695cff91c5b702ef203cebedcc24d73106ccbdacb3c89f6169f1c4b18d',
          'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-minimal:centos6.5': 'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-minimal@sha256:92711e47df350c0e40f8d47194e315f9391af7ea704458bbe7fe24ac99755732',
          'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-omp:centos6.5': 'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-omp@sha256:f7902184b1c4ce03df5a3d4e3aecd39574716305ee2c93364f9e0c79608fe0ed',
          'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-minimal:centos6.8': 'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-minimal@sha256:893b112c71a544531a492f61094b4a26e57872dac2214361015e279db606aaca',
          'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-omp:centos6.8': 'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-omp@sha256:89bf2c8e37dbdbc4f63ed5b2ec76963ff27dfcd6ad47121a3d5d96a4533c0815',
          'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-gpu:centos6.9': 'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-gpu@sha256:829c221242eb47b69ae664815a3a17ba54f9184d05e14a307c4a2e919c2a2b68',
          'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-minimal:centos7.3': 'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-minimal@sha256:a7f96b93303bb78c98eed6eb4a69df570cdf3a99238e5e621074473766dbc33a',
          'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-omp:centos7.3': 'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-omp@sha256:5612935fbb0665df6f7315f062349f2be632741d2ad8efab1926666b8e982352',
          'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-gpu:centos7.4': 'docker.h2o.ai/opsh2oai/h2o-3-xgb-runtime-gpu@sha256:af8169a4edc0368da20176241514e4c05edf8ea3a54ec8950f428e665423e209',
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
