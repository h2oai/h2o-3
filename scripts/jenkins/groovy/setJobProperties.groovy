def call(buildConfig, customProperties=null) {
    def jobProperties = [
            parameters(
                    [
                            booleanParam(defaultValue: buildConfig.getDefaultOverrideRerun(), description: 'If checked, execute all stages regardless of the commit message content. If not checked and the message contains !rerun, only stages failed in previous build will be executed.', name: 'overrideRerun')
                    ]
            ),
            buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '25'))
    ]
    if (customProperties != null) {
        jobProperties += customProperties
    }
    properties(
        jobProperties
    )
}

return this
