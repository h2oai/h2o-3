class BuildConfig {

  private String mode;
  private String nodeLabel;
  private String commitMessage;
  private boolean defaultOverrideRerun = false;
  private LinkedHashMap changesMap

  def initialize(final String mode, final String nodeLabel, final String commitMessage, final LinkedHashMap changesMap) {
    this.mode = mode
    this.nodeLabel = nodeLabel
    this.commitMessage = commitMessage
    this.changesMap = changesMap
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

}

return new BuildConfig()
