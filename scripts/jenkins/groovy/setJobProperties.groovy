def call(buildConfig) {
  properties(
    [
      parameters(
        [
          booleanParam(defaultValue: buildConfig.getDefaultOverrideRerun(), description: 'If checked, execute all stages regardless of the commit message content. If not checked and the message contains !rerun, only stages failed in previous build will be executed.', name: 'overrideRerun')
        ]
      ),
      buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '25'))
    ]
  )
  currentBuild.description = buildConfig.getCommitMessage()
}

return this
