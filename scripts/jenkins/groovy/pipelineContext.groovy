import ai.h2o.ci.buildsummary.BuildSummary
import ai.h2o.ci.Emailer

def call(final String h2o3Root, final String mode, final scmEnv, final boolean ignoreChanges) {
    final String BUILD_CONFIG_SCRIPT_NAME = 'buildConfig.groovy'
    final String PIPELINE_UTILS_SCRIPT_NAME = 'pipelineUtils.groovy'

    env.COMMIT_MESSAGE = sh(script: "cd ${h2o3Root} && git log -1 --pretty=%B", returnStdout: true).trim()
    env.BRANCH_NAME = scmEnv['GIT_BRANCH'].replaceAll('origin/', '')
    env.GIT_SHA = scmEnv['GIT_COMMIT']
    env.GIT_DATE = sh(script: "cd ${h2o3Root} && git show -s --format=%ci", returnStdout: true).trim()

    def final buildConfigFactory = load("${h2o3Root}/scripts/jenkins/groovy/${BUILD_CONFIG_SCRIPT_NAME}")
    def final pipelineUtilsFactory = load("${h2o3Root}/scripts/jenkins/groovy/${PIPELINE_UTILS_SCRIPT_NAME}")
    def final pipelineUtils = pipelineUtilsFactory()

    def final buildinfoPath = "${h2o3Root}/h2o-dist/buildinfo.json"

    def final pipelineUtils = pipelineUtilsFactory()

    return new PipelineContext(
            buildConfigFactory(this, mode, env.COMMIT_MESSAGE, getChanges(h2o3Root), ignoreChanges,
                    pipelineUtils.readSupportedHadoopDistributions(this, buildinfoPath)
            ),
            new BuildSummary(true),
            pipelineUtils,
            new Emailer()
    )
}

private List<String> getChanges(final String h2o3Root) {
    sh """
        cd ${h2o3Root}
        git fetch --no-tags --progress https://github.com/h2oai/h2o-3 +refs/heads/master:refs/remotes/origin/master
    """
    final String mergeBaseSHA = sh(script: "cd ${h2o3Root} && git merge-base HEAD origin/master", returnStdout: true).trim()
    return sh(script: "cd ${h2o3Root} && git diff --name-only ${mergeBaseSHA}", returnStdout: true).trim().tokenize('\n')
}

class PipelineContext{

    private final buildConfig
    private final BuildSummary buildSummary
    private final pipelineUtils
    private final Emailer emailer

    private PipelineContext(final buildConfig, final BuildSummary buildSummary, final pipelineUtils, final Emailer emailer) {
        this.buildConfig = buildConfig
        this.buildSummary = buildSummary
        this.pipelineUtils = pipelineUtils
        this.emailer = emailer
    }

    def getBuildConfig() {
        return buildConfig
    }

    BuildSummary getBuildSummary() {
        return buildSummary
    }

    def getUtils() {
        return pipelineUtils
    }

    Emailer getEmailer() {
        return emailer
    }

}

return this