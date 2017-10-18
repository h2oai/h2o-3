class BuildConfig {

  private String mode;
  private String nodeLabel;
  private String commitMessage;
  private boolean defaultOverrideRerun = false;

  def initialize(final String mode, final String nodeLabel, final String commitMessage) {
    this.mode = mode
    this.nodeLabel = nodeLabel
    this.commitMessage = commitMessage
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

  String toString() {
    return """
    Mode: ${getMode()}
    Node Label: ${getNodeLabel()}
    Commit Message: ${getCommitMessage()}
    Default for Override Rerun: ${getDefaultOverrideRerun()}
    """
  }

}

return new BuildConfig()
