class BuildConfig {

  // Specifies tag of the docker image used in ALL of the pipelines
  public static final String DOCKER_IMAGE_VERSION_TAG = '82'

  public static final String LANG_PY = 'py'
  public static final String LANG_R = 'r'
  public static final String LANG_JS = 'js'
  public static final String LANG_JAVA = 'java'
  // Use to indicate, that the stage is not component dependent such as MOJO Compatibility Test,
  // always run
  public static final String LANG_NONE = 'none'

  private String mode
  private String nodeLabel
  private String commitMessage
  private boolean defaultOverrideRerun = false
  private LinkedHashMap changesMap = [
    (LANG_PY): false,
    (LANG_R): false,
    (LANG_JS): false,
    (LANG_JAVA): false,
    (LANG_NONE): true
  ]

  def initialize(final String mode, final String nodeLabel, final String commitMessage, final List<String> changes, final boolean overrideDetectionChange) {
    this.mode = mode
    this.nodeLabel = nodeLabel
    this.commitMessage = commitMessage
    if (overrideDetectionChange) {
      markAllLangsForTest()
    } else {
      detectChanges(changes)
    }
  }

  def getMode() {
    return mode
  }

  def getNodeLabel() {
    return nodeLabel
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

  String toString() {
    return """
    Mode: ${getMode()}
    Node Label: ${getNodeLabel()}
    Commit Message: ${getCommitMessage()}
    Default for Override Rerun: ${getDefaultOverrideRerun()}
    Changes: ${changesMap}
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

}

return new BuildConfig()
