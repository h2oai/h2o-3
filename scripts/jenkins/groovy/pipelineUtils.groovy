def call() {
    return new PipelineUtils()
}

class PipelineUtils {

    private static final String PIPELINE_SCRIPTS_STASH_NAME = 'pipeline_scripts'

    String stageNameToDirName(stageName) {
        if (stageName != null) {
            return stageName.toLowerCase().replace(' ', '-')
        }
        return null
    }

    void stashScripts(final context) {
        context.stash name: PIPELINE_SCRIPTS_STASH_NAME, includes: 'h2o-3/scripts/jenkins/groovy/*', allowEmpty: false
    }

    void unstashScripts(final context) {
        context.unstash name: PIPELINE_SCRIPTS_STASH_NAME
    }
}

return this