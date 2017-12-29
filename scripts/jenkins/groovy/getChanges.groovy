def call(final String h2o3DirPath=null) {
    def cdCmd = ''
    if (h2o3DirPath != null) {
        cdCmd = "cd ${h2o3DirPath} &&"
    }
    sh "${cdCmd} git fetch --no-tags --progress https://github.com/h2oai/h2o-3 +refs/heads/master:refs/remotes/origin/master"
    def mergeBaseSHA = sh(script: "${cdCmd} git merge-base HEAD origin/master", returnStdout: true).trim()
    return sh(script: "${cdCmd} git diff --name-only ${mergeBaseSHA}", returnStdout: true).trim().tokenize('\n')
}

return this