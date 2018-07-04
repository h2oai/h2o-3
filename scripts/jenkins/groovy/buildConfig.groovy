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

          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.2:47": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.2@sha256:296a58b91a0695cfd74c43987af4e89770b486793f31c2c4e816d4c78fb3fca7",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.3:47": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.3@sha256:add69b2164dd07c31c27bdf0b723657c188579657186cecdd5e0da467405d17f",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.4:47": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.4@sha256:a78c92175bd52fd0aa62ea062bfd465492af62955144bfee896e16e10e7c5fc1",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.5:47": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.5@sha256:7d52bfa47d9ba255dc94acf1ba36dba241f30ff32932e039f461e9f09863c09e",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.6:47": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.6@sha256:a710a17fe7983a0021c38531efe90dce2a2aec58d36ff10687acee280a5efcf0",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.2-krb:47": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.2-krb@sha256:0cf627ba930236e5cc997222c3b78adaaf9ba7e9e9d3d6e0ced7cbd8f12268b1",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.3-krb:47": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.3-krb@sha256:94c6375c1a7671342cda1bedf9e058adabd1d3def9aae7c99687382f134ef51a",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.4-krb:47": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.4-krb@sha256:3046e45a0e3011915f6fe88430c0623720ad652edde96e33de60f4ae20f6b719",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.5-krb:47": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.5-krb@sha256:ac717d0732d4ee71c149a33371f9dddedc8b84122b7275ae0f4ee5c42c2b0d4e",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.6-krb:47": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.6-krb@sha256:7000402e86f3b3bb441a385048493409a02dfa4a998d19bd2cc6dc9ff6694709",

          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.4:47": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.4@sha256:4e2d054d83d335eef7dac2260ec4dbcdd7af092fc81626b1d2fd84ecbb5b1d85",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.5:47": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.5@sha256:0a63515cf6f4f63ad2e17cb891b182dc4a47338f5e7cf072326b9d035a7f70de",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.6:47": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.6@sha256:d0648437e63bd6fc915e8d8467b6b6af119c470c76b2426691ff55322df63d93",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.7:47": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.7@sha256:e135c2c0b6bd25e3fc606f8b28c8a0e9d64a90c308281dd64649831b78e30821",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.8:47": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.8@sha256:cfbbc4162d1e822b379248c1d2dca8384e8477b230bfde734d07ca6913fe7ff1",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.10:47": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.10@sha256:e77fc4bfffd892a7c7bbf8348f4a7c86aea63722c4704230d550621a3e5f17de",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.13:47": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.13@sha256:9a7815c2a628214258f2c4e5c53fe2e475a4f6d043beaaf2c4600ac4fecdc0c3",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.14:47": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.14@sha256:5fd50826f4a4ae31343be1d82e8894b5aefac484e87ce935eb2fb49427975b3a",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.4-krb:47": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.4-krb@sha256:5574bd648cc04b2b7d6688d3f49dd6dc9e6fc0fb07b4f9d4f157d5d86a1d0fed",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.5-krb:47": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.5-krb@sha256:70794eca5f34d66b711c3dcc57b403a5fc0014e6bd658bba51f7340f35f9c543",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.6-krb:47": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.6-krb@sha256:0d682266e481b662b7298fe0e97b2cacd5172b34354fdb4ad09f16278ab16fe9",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.7-krb:47": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.7-krb@sha256:7a28fb367ae9605716c83b1d34f7b5fce08e587304c0210d0ead076ddbcb9713",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.8-krb:47": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.8-krb@sha256:b00efbfe925826d73dcacc6f1d7a2a2544c3047c572781335d1e1a9862a6254f",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.10-krb:47": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.10-krb@sha256:d446bfe3d5e0ddcea069b34c14f15e0ba3927349293624422bc6bab6b9396008",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.13-krb:47": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.13-krb@sha256:b60eac1f1f85e1d03308d4056dbc214de0eb46003925f9de14dc9b770144da8b",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.14-krb:47": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.14-krb@sha256:c4107af03a3582068efede1af59a834b85e25c01238a12050ce249caf4b94de0",

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
