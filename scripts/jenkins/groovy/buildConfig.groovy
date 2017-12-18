class BuildConfig {

  public static enum JenkinsMaster {
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

  public static enum NodeLabels {
    LABELS_C1('docker && !mr-0xc8', 'mr-0xc9'),
    LABELS_B4('docker', 'docker')

    private String defaultNodeLabel
    private String benchmarkNodeLabel

    private NodeLabels(final String defaultNodeLabel, final String benchmarkNodeLabel) {
      this.defaultNodeLabel = defaultNodeLabel
      this.benchmarkNodeLabel = benchmarkNodeLabel
    }

    public String getDefaultNodeLabel() {
      return defaultNodeLabel
    }

    public String getBenchmarkNodeLabel() {
      return benchmarkNodeLabel
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

  public static final String DOCKER_REGISTRY = 'docker.h2o.ai'
  public static final String PIPELINE_SCRIPTS_STASH_NAME = 'pipeline_scripts'

  private static final String DEFAULT_IMAGE_NAME = 'h2o-3-runtime'
  private static final String DEFAULT_IMAGE_VERSION_TAG = '102'
  // This is the default image used for tests, build, etc.
  public static final String DEFAULT_IMAGE = DOCKER_REGISTRY + '/opsh2oai/' + DEFAULT_IMAGE_NAME + ':' + DEFAULT_IMAGE_VERSION_TAG

  private static final String BENCHMARK_IMAGE_NAME = 'h2o-3-benchmark'
  private static final String BENCHMARK_IMAGE_VERSION_TAG = '117'
  // Use this image for benchmark stages
  public static final String BENCHMARK_IMAGE = DOCKER_REGISTRY + '/opsh2oai/' + BENCHMARK_IMAGE_NAME + ':' + BENCHMARK_IMAGE_VERSION_TAG

  private static final String HADOOP_IMAGE_NAME_PREFIX = 'h2o-3-hadoop'
  private static final String HADOOP_IMAGE_VERSION_TAG = '45'

  public static final String LANG_PY = 'py'
  public static final String LANG_R = 'r'
  public static final String LANG_JS = 'js'
  public static final String LANG_JAVA = 'java'
  public static final String LANG_HADOOP = 'hadoop'
  // Use to indicate, that the stage is not component dependent such as MOJO Compatibility Test,
  // always run
  public static final String LANG_NONE = 'none'

  public static final String H2O_OPS_TOKEN = 'h2o-ops-personal-auth-token'
  private static final String COMMIT_STATE_PREFIX = 'H2O-3 Pipeline'

  public static final String AWS_CREDENTIALS_ID = 'AWS S3 Credentials'

  private String mode
  private String nodeLabel
  private String commitMessage
  private buildSummary
  private boolean defaultOverrideRerun = false
  private boolean buildHadoop
  private List hadoopDistributionsToBuild
  private String majorVersion
  private String buildVersion
  private JenkinsMaster master
  private NodeLabels nodeLabels
  private LinkedHashMap changesMap = [
    (LANG_PY): false,
    (LANG_R): false,
    (LANG_JS): false,
    (LANG_JAVA): false,
    (LANG_NONE): true
  ]

  def initialize(final Script context, final String mode, final String commitMessage, final List<String> changes,
                 final boolean overrideDetectionChange, final buildSummary, final boolean buildHadoop) {
    this.mode = mode
    this.nodeLabel = nodeLabel
    this.commitMessage = commitMessage
    this.buildSummary = buildSummary
    this.buildHadoop = buildHadoop
    if (overrideDetectionChange) {
      markAllLangsForTest()
    } else {
      detectChanges(changes)
    }
    master = JenkinsMaster.findByBuildURL(context.env.BUILD_URL)
    nodeLabels = NodeLabels.findByJenkinsMaster(master)
  }

  def getMode() {
    return mode
  }

  def getCommitMessage() {
    return commitMessage
  }

  def setDefaultOverrideRerun(final boolean defaultOverrideRerun) {
    this.defaultOverrideRerun = defaultOverrideRerun
  }

  boolean getDefaultOverrideRerun() {
    return this.defaultOverrideRerun
  }

  def commitMessageContains(final String keyword) {
    return commitMessage.contains(keyword)
  }

  def langChanged(final String lang) {
    return changesMap[lang]
  }

  def getMajorVersion() {
    return majorVersion
  }

  def getBuildVersion() {
    return buildVersion
  }

  JenkinsMaster getMaster() {
    return master
  }

  String getDefaultNodeLabel() {
    return nodeLabels.getDefaultNodeLabel()
  }

  String getBenchmarkNodeLabel() {
    return nodeLabels.getBenchmarkNodeLabel()
  }

  boolean getBuildHadoop() {
    return buildHadoop
  }

  void setSupportedHadoopDistributions(final List distributionsToBuild) {
    this.hadoopDistributionsToBuild = distributionsToBuild
  }

  List getSupportedHadoopDistributions() {
    return hadoopDistributionsToBuild
  }

  String toString() {
    return """
=======================================================================
    Major Version:              | ${getMajorVersion()}
    Build Version:              | ${getBuildVersion()}
    Mode:                       | ${getMode()}
    Default Node Label:         | ${getDefaultNodeLabel()}
    Benchmark Node Label:       | ${getBenchmarkNodeLabel()}
    Commit Message:             | ${getCommitMessage()}
    Default for Override Rerun: | ${getDefaultOverrideRerun()}
    Runtime image:              | ${DEFAULT_IMAGE}
    Benchmark image:            | ${BENCHMARK_IMAGE}
    Changes:                    | ${changesMap}
=======================================================================
    """
  }

  private void detectChanges(List<String> changes) {
    // clear the changes map
    markAllLangsForSkip()
    // stages for lang none should be executed always
    changesMap[LANG_NONE] = true

    for (change in changes) {
      if (change.startsWith('h2o-py/') || change == 'h2o-bindings/bin/gen_python.py') {
        changesMap[LANG_PY] = true
      } else if (change.startsWith('h2o-r/') ||  change == 'h2o-bindings/bin/gen_R.py') {
        changesMap[LANG_R] = true
      } else if (change.endsWith('.md')) {
        // no need to run any tests if only .md files are changed
      } else {
        markAllLangsForTest()
      }
    }
  }

  private void markAllLangsForTest() {
    changesMap.each { k,v ->
      changesMap[k] = true
    }
  }

  private void markAllLangsForSkip() {
    changesMap.each { k,v ->
      // mark no changes for all langs except LANG_NONE
      changesMap[k] = k == LANG_NONE
    }
  }

  public void readVersion(final String versionFileContent) {
    versionFileContent.split('\n').each{ line ->
      if (line.startsWith('Version: ')) {
        def versionString = line.replace('Version: ', '')
        this.majorVersion = versionString.split('\\.')[0..2].join('.')
        this.buildVersion = versionString.split('\\.')[3..-1].join('.')
      }
    }
  }

  public GString getGitHubCommitStateContext(final String stageName) {
    return "${COMMIT_STATE_PREFIX} » ${stageName}"
  }

  String getSmokeHadoopImage(final String distribution, final String version) {
    return "${DOCKER_REGISTRY}/opsh2oai/${HADOOP_IMAGE_NAME_PREFIX}-${distribution}-${version}:${HADOOP_IMAGE_VERSION_TAG}"
  }

  void addStageSummary(final context, final String stageName) {
    buildSummary.addStageSummary(stageName)
    updateJobDescription(context)
  }

  void markStageSuccessful(final context, final String stageName) {
    buildSummary.setStageResult(stageName, buildSummary.RESULT_SUCCESS)
    updateJobDescription(context)
  }

  void markStageFailed(final context, final String stageName) {
    buildSummary.setStageResult(stageName, buildSummary.RESULT_FAILURE)
    updateJobDescription(context)
  }

  void setStageNode(final context, final String stageName, final String nodeName) {
    buildSummary.setStageNode(stageName, nodeName)
    updateJobDescription(context)
  }

  def getBuildSummary() {
    return buildSummary
  }

  void updateJobDescription(final context) {

    def stagesTable = ''
    if (!buildSummary.getStageSummaries().isEmpty()) {
      def stagesTableBody = ''
      for (stageSummary in buildSummary.getStageSummaries()) {
        def nodeName = stageSummary['nodeName'] == null ? 'Not yet allocated' : stageSummary['nodeName']
        def result = stageSummary['result'] == null ? 'Pending' : stageSummary['result']
        stagesTableBody += """
          <tr style="background-color: ${stageResultToBgColor(stageSummary['result'])}">
            <td style="border: 1px solid black; padding: 0.2em 1em">${stageSummary['stageName']}</td>
            <td style="border: 1px solid black; padding: 0.2em 1em">${nodeName}</td>
            <td style="border: 1px solid black; padding: 0.2em 1em">${result.capitalize()}</td>
          </tr>
        """
      }

      stagesTable = """
        <table style="margin-left: 1em; border-collapse: collapse">
          <thead>
            <tr>
              <th style="border: 1px solid black; padding: 0.5em">Name</th>
              <th style="border: 1px solid black; padding: 0.5em">Node</th>
              <th style="border: 1px solid black; padding: 0.5em">Result</th>
            </tr>
          </thead>
          <tbody>
            ${stagesTableBody}
          </tbody>
        </table>
      """
    }

    context.currentBuild.description = """
      <div>
        <h3>
          Details
        </h3>
        <p style="margin-left: 1em"><strong>Commit Message:</strong> ${commitMessage}</p>
        <p style="margin-left: 1em"><strong>SHA:</strong> ${context.env.GIT_SHA}</p>
        ${stagesTable}  
      </div>
    """
  }

  private String stageResultToBgColor(final String result) {
    def BG_COLOR_SUCCESS = '#7fce67'
    def BG_COLOR_FAILURE = '#d56060'
    def BG_COLOR_OTHER = '#fbf78b'

    if (result == buildSummary.RESULT_SUCCESS) {
      return BG_COLOR_SUCCESS
    }
    if (result == buildSummary.RESULT_FAILURE) {
      return BG_COLOR_FAILURE
    }
    return BG_COLOR_OTHER
  }

}

return new BuildConfig()
