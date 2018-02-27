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

    /**
     *
     * @param stageName
     * @return true if the stage with stageName was present in previous build and did succeed.
     */
    @NonCPS
    boolean wasStageSuccessful(final context, String stageName) {
        // displayName of the relevant end node.
        def STAGE_END_TYPE_DISPLAY_NAME = 'Stage : Body : End'

        // There is no previous build, the stage cannot be successful.
        if (context.currentBuild.previousBuild == null) {
            context.echo "###### No previous build available, marking ${stageName} as FAILED. ######"
            return false
        }

        // Get all nodes in previous build.
        def prevBuildNodes = context.currentBuild.previousBuild.rawBuild
                .getAction(org.jenkinsci.plugins.workflow.job.views.FlowGraphAction.class)
                .getNodes()
        // Get all end nodes of the relevant stage in previous build. We need to check
        // the end nodes, because errors are being recorded on the end nodes.
        def stageEndNodesInPrevBuild = prevBuildNodes.findAll{it.getTypeDisplayName() == STAGE_END_TYPE_DISPLAY_NAME}
                .findAll{it.getStartNode().getDisplayName() == stageName}

        // If there is no start node for this stage in previous build that means the
        // stage was not present in previous build, therefore the stage cannot be successful.
        def stageMissingInPrevBuild = stageEndNodesInPrevBuild.isEmpty()
        if (stageMissingInPrevBuild) {
            context.echo "###### ${stageName} not present in previous build, marking this stage as FAILED. ######"
            return false
        }

        // If the list of end nodes for this stage having error is empty, that
        // means the stage was successful. The errors are being recorded on the end nodes.
        return stageEndNodesInPrevBuild.find{it.getError() != null} == null
    }

    void unpackTestPackage(final context, final String component, final String stageDir) {
        context.echo "###### Pulling test package. ######"
        context.step([$class              : 'CopyArtifact',
                      projectName         : context.env.JOB_NAME,
                      fingerprintArtifacts: true,
                      filter              : "h2o-3/test-package-${component}.zip, h2o-3/build/h2o.jar",
                      selector            : [$class: 'SpecificBuildSelector', buildNumber: context.env.BUILD_ID],
                      target              : stageDir + '/'
        ])
        context.sh "cd ${stageDir}/h2o-3 && unzip -q -o test-package-${component}.zip && rm test-package-${component}.zip"
    }

    void archiveStageFiles(final context, final String h2o3dir, final List<String> archiveFiles, final List<String> excludeFiles) {
        List<String> excludes = []
        if (excludeFiles != null) {
            excludes = excludeFiles
        }
        context.archiveArtifacts artifacts: archiveFiles.collect{"${h2o3dir}/${it}"}.join(', '), allowEmptyArchive: true, excludes: excludes.collect{"${h2o3dir}/${it}"}.join(', ')
    }

    void archiveJUnitResults(final context, final h2o3dir) {
        context.junit testResults: "${h2o3dir}/**/test-results/*.xml", allowEmptyResults: true, keepLongStdio: true
    }

}

return this