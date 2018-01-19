import groovy.json.JsonSlurper

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

    void stashFiles(final context, final String stashName, final String includedFiles) {
        context.stash name: stashName, includes: includedFiles, allowEmpty: false
    }

    void unstashFiles(final context, final String stashName) {
        context.unstash stashName
    }

    void stashScripts(final context) {
        context.stash name: PIPELINE_SCRIPTS_STASH_NAME, includes: 'h2o-3/scripts/jenkins/groovy/*', allowEmpty: false
    }

    void unstashScripts(final context) {
        context.unstash name: PIPELINE_SCRIPTS_STASH_NAME
    }

    List<String> readSupportedHadoopDistributions(final context, final String buildinfoPath) {
        final List<String> DOCKERIZED_DISTRIBUTIONS = ['cdh', 'hdp']

        final String buildinfoContent = context.sh(script: "sed 's/SUBST_BUILD_TIME_MILLIS/\"SUBST_BUILD_TIME_MILLIS\"/g' ${buildinfoPath} | sed -e 's/SUBST_BUILD_NUMBER/\"SUBST_BUILD_NUMBER\"/g'", returnStdout: true).trim()

        def buildinfo = new JsonSlurper().parseText(buildinfoContent)

        def distributionsToBuild = []

        for (distSpec in buildinfo.hadoop_distributions) {
            def distributionStr = distSpec.distribution.toLowerCase()
            for (dockerizedDist in DOCKERIZED_DISTRIBUTIONS) {
                if (distributionStr.startsWith(dockerizedDist)) {
                    def distributionName = dockerizedDist
                    def distributionVersion = distributionStr.replaceFirst(dockerizedDist, '')
                    distributionsToBuild += [
                            name: distributionName,
                            version: distributionVersion
                    ]
                    context.echo "Supported dist found: dist: ${distributionName}, ver: ${distributionVersion}"
                }
            }
        }

        return distributionsToBuild
    }

    boolean dockerImageExistsInRegistry(final context, final String registry, final String imageName, final String version) {
        context.withCredentials([context.usernamePassword(credentialsId: "${registry}", usernameVariable: 'REGISTRY_USERNAME', passwordVariable: 'REGISTRY_PASSWORD')]) {
            final String response = "curl -k -u ${context.REGISTRY_USERNAME}:${context.REGISTRY_PASSWORD} https://${registry}/v2/${imageName}/tags/list".execute().text

            final def jsonResponse = new groovy.json.JsonSlurper().parseText(response)
            if (jsonResponse.errors || jsonResponse.tags == null) {
                context.echo "response: ${response}"
                context.error "Docker registry check failed."
            }

            return jsonResponse.tags.contains(version)
        }
    }
}

return this