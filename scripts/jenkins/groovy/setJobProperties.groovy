def call() {
  properties(
    [
        buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '25'))
    ]
  )
  currentBuild.description = sh(script: 'cd h2o-3 && git log -1 --pretty=%B', returnStdout: true).trim()
}

return this
