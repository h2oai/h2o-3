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

          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.2:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.2@sha256:eacd8b44fefae31e244f99da2968407f5f4aeaca7442fa289dea842234089c25",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.3:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.3@sha256:56f460ea36d1cdc4b72d2e808e75419fd34cc713a775e1a1b00bb2dcd5f5f6eb",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.4:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.4@sha256:755ee5ac48aca1d4a4d492a52a89808e1c126a2d9ee8b2bff2ebd7de364ecbf1",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.5:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.5@sha256:c49ae1e45f6af1123f30bb902035d137734224d0b084a62468618833a6e1aece",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.6:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.6@sha256:c686f0c48dfd171a6bb06514be251d283e88851ff52e44fc95666a243d2a7e9a",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.2-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.2-krb@sha256:79f08bc3315bfe7aa6f164baaa7366d8a047c209f8f80564f0f4f60415539edf",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.3-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.3-krb@sha256:259087e0907b7d5f8fe5d5f9c77c3012f836dc00cc4d3e8f0785ab9a2f3a16e1",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.4-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.4-krb@sha256:215c67eb0b76e9aac16a3a96cd238373d87031b676f4b27b3b469cd77f64dd1e",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.5-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.5-krb@sha256:55c5319527da0a56a3d6a6ba94302466f904a6bbd48f3dfb72a383360c5f6bb3",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.6-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-hdp-2.6-krb@sha256:351ff6c24687e7c9c8c3fabf488125aeb27320bdfe1118efc2386f3a1ae2886a",

          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.4:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.4@sha256:e9393cccf6159f9a7a53410745d8d8f1445fc29c1e5c0dbabd248541687f8a2f",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.5:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.5@sha256:fb45fbca777c7e9a168d273707c1cb817a7e04db535c165a2108cbb1bc763ea8",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.6:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.6@sha256:aa63a4030283b72db0ab0d9c4fa9e48a762fff6a665ba7ef054502aa59451d2c",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.7:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.7@sha256:1f7ddf5cc5f2565d45016639e7f3a6cb308af88cc2f6da17c800697a9a7b1693",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.8:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.8@sha256:d0046e24b9c3d0a8e068cf331dbc266da05d4e51c561031dbfdfbcac97239dc4",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.10:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.10@sha256:a9ffa7e8370b44571a1deb8c82941c7d97977387f0081d77680d12536188e847",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.13:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.13@sha256:7dbddac400802b2f174159323a9d8cf2857826e9a4ab2d71b66e6ce5da51a2cf",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.14:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.14@sha256:7bf57004363a218b36a2f9cc5147705fb257aae0cbc1dc498027ffa42028cc24",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.4-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.4-krb@sha256:f168643bb4bc0c691437e99f84a05fb9b7798052f518a9357e3b608ca29b1e4c",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.5-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.5-krb@sha256:83efd4d9d1d6eccc77cbdda3249b5ce9f2cc755d604dce6978a15e896ab893b3",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.6-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.6-krb@sha256:d1fd7ffe917aae1ae6b4db8756a277e398c2cf69d680d189c31211ec24933f03",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.7-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.7-krb@sha256:e62b749c3c0029e2481855d7cfd6fe93da38824a19eecf5ce866b8956bd1adc3",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.8-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.8-krb@sha256:596beaea6dceecac4cd81764d332da0fee949780d4f52a79f13f5f98fa95d802",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.10-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.10-krb@sha256:ef3ca2e645b28c848da16c24879a8e33a5585d6de988e871eaaa8d509dcc39ed",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.13-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.13-krb@sha256:c1e82347f96204ae7e3b675cc1b7fe7c6e89b46af5751a7c8dc67c74a79786a0",
          "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.14-krb:48": "docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-5.14-krb@sha256:6acb6da54339b0bfa65de6234720575533f67f5bfb2932fefad0c1f32695d723",

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
