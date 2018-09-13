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

          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.2:48":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.2@sha256:9e7cd9f4a1a7cf3ee3e98105953c0e834ffa12df6eedcf9f8eee3a1b677bf2f8",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.2-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.2-krb@sha256:907030f3afd48024d2bfea77804854f5954655e8cc97b9158c365f85814d63f4",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.3:48":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.3@sha256:954032d87690c1481d0cec9964bac1e532bf854e430dd9ec3c3c6906998b42c7",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.3-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.3-krb@sha256:2a944130ae2c63d647203cc666aea81e24aca78143c6ae70942449084bf7ff45",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.4:48":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.4@sha256:1dcf09000312f564ac7ea54451bedfa6a31afe5e4e39f483342fd3f067f56ced",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.4-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.4-krb@sha256:660053da9e7ac6a950345c1393f7b0e159b961e937e25b5a31bf903c79c540a5",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.5:48":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.5@sha256:4326333759a8876af589e0806d91f7509736b608d7dda3a49aef6e9645ffa43a",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.5-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.5-krb@sha256:01e202d8872086aa5fe6dc5e8f1efd84e785f768cbf27ba99a5e9a1987896351",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.6:48":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.6@sha256:66230754bd337de9ecd7e8abe6c2edd961eef869320adc8f75b665b58d652634",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.6-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.6-krb@sha256:f042b804de6fd0c514bb83390b2910b7493e7857923559b51203c9301e19fcbb",

          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.4:48":      "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.4@sha256:18898aca8d908e0790ae9c424c20f8227f89a5190f6fb5b89154b9b897ecd857",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.4-krb:48":  "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.4-krb@sha256:76ae95671b4fefe29109abca579296dcd75eba9399c4486f752c3ebb43fb65c8",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.5:48":      "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.5@sha256:2921f653288f93bf1e7dffb4ed4f356bded19476ced377959d6a5e5525cbb7d2",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.5-krb:48":  "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.5-krb@sha256:bb2c04d3c9ddf15a406838f3bd2846c54a944c5d946e44aaae22f314ee63e716",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.6:48":      "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.6@sha256:37d559408142b719dcdb7def7867fc0bf6f7528dd8abfe89f9d776e21f03c850",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.6-krb:48":  "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.6-krb@sha256:4983811c8eaee148ee1ac229cdf7f260ea8d39ba88bf52019114f2f5ed70cc48",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.7:48":      "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.7@sha256:545286c68fcbaa14d6fce66a94e5312e2061a695e49d3ced8fd6185c994db59b",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.7-krb:48":  "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.7-krb@sha256:55b7ddaff275384e78ef02cc25e63af0d01c7e906e9451454d8cf0f2de75b4ce",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.8:48":      "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.8@sha256:93438a0b21926364ff391fbdce3fa8362e473638752f338e994388879674f779",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.8-krb:48":  "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.8-krb@sha256:b1b6b94dad1b04c50901cf08bf858ee75dfbce8232de18a6bf62112b873a6bc3",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.9:48":      "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.9@sha256:1a4216952b35ff0b2d0e0fd1cab0ef9cb8a92b17a6f4ad0fdc86e0a12df9c7a1",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.9-krb:48":  "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.9-krb@sha256:cf3daec56ee6b793c64efff0b6c395658a6806158c15b1a8ee7c4ebecfccec06",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.10:48":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.10@sha256:645cdacdc5dd53856151efdddb4e22a807b51a40e3363745a4e7d53573a27669",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.10-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.10-krb@sha256:32d8c1c9f9357055686d296c04930e0f8dc6e2e8848271f94ab07df567ecb8d9",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.13:48":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.13@sha256:dc82d5a39912c90d4101ded6ec2ba107069827865c8d515d3d348dab4447b2e0",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.13-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.13-krb@sha256:4c89348de8ad7daadbba4e1f1a21969b45ae8ad85f04e7e5e5487aa267c6263f",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.14:48":     "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.14@sha256:1f73ae2a9692b0349bd4829033c8dbc0528dc09aa7d9db8b48ee2f0013a03249",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.14-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.14-krb@sha256:52569764d38cc3c870b9c073d9d27476d3b7f21d6e5f042a5024c26f42df02ad",

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
