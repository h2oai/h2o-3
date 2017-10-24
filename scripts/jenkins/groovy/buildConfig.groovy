class BuildConfig {

  public static final String LANG_PY = 'py'
  public static final String LANG_R = 'r'
  public static final String LANG_JS = 'js'
  public static final String LANG_JAVA = 'java'

  private String mode
  private String nodeLabel
  private String commitMessage
  private boolean defaultOverrideRerun = false
  private LinkedHashMap changesMap

  def initialize(final String mode, final String nodeLabel, final String commitMessage, final List<String> changes) {
    this.mode = mode
    this.nodeLabel = nodeLabel
    this.commitMessage = commitMessage
    detectChanges(changes)
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

  def markAllLangsForTest() {
    changesMap.each { k,v ->
      changesMap[k] = true
    }
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
    changesMap = [
      (LANG_PY): false,
      (LANG_R): false,
      (LANG_JS): false,
      (LANG_JAVA): false
    ]

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

}

return new BuildConfig()
