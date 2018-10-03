import groovy.json.JsonSlurper

def call() {
    return new PipelineUtils()
}

class PipelineUtils {

    private static final String PIPELINE_SCRIPTS_STASH_NAME = 'pipeline_scripts'

    String stageNameToDirName(stageName) {
        if (stageName != null) {
            return stageName.toLowerCase().replaceAll(' |\\(|\\)', '-')
        }
        return null
    }

    void stashFiles(final context, final String stashName, final String includedFiles, final boolean allowEmpty) {
        context.stash name: stashName, includes: includedFiles, allowEmpty: allowEmpty
    }

    void stashFiles(final context, final String stashName, final String includedFiles) {
        stashFiles(context, stashName, includedFiles, false)
    }

    void stashXGBoostWheels(final context, final String xgbVersion) {
        context.echo "Preparing to stash whls for XGBoost ${xgbVersion}"
        try {
            context.echo "Trying to pull from Jenkins archives"
            context.copyArtifacts(
                    projectName: 'h2o-3-xgboost4j-release-pipeline/h2o3',
                    selector: context.specific(xgbVersion.split('\\.').last()),
                    filter: 'linux-ompv4/ci-build/*.whl',
                    flatten: true,
                    fingerprintArtifacts: true,
                    target: 'h2o-3/xgb-whls'
            )
        } catch (ignore) {
            context.echo "Pull from Jenkins archives failed, loading from S3"
            context.sh """
                mkdir -p h2o-3/xgb-whls
                s3cmd get s3://h2o-release/xgboost/h2o3/${xgbVersion}/*.whl h2o-3/xgb-whls/
            """
        }
        final String whlsPath = 'h2o-3/xgb-whls/*.whl'
        context.echo "********* Stash XGBoost wheels *********"
        stashFiles(context, 'xgb-whls', whlsPath, false)
    }

    void unstashFiles(final context, final String stashName) {
        context.unstash stashName
    }

    void pullXGBWheels(final context) {
        unstashFiles(context, 'xgb-whls')
    }

    void stashScripts(final context) {
        stashFiles(context, PIPELINE_SCRIPTS_STASH_NAME, 'h2o-3/scripts/jenkins/groovy/*', false)
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

    def readCurrentXGBVersion(final context, final h2o3Root) {
        final def xgbVersion = context.sh(script: "cd ${h2o3Root} && cat h2o-genmodel-extensions/xgboost/build.gradle | grep ai.h2o:xgboost4j: | egrep -o '([0-9]+\\.+)+[0-9]+'", returnStdout: true).trim()
        context.echo "XGBoost Version: ${xgbVersion}"
        if (xgbVersion == null || xgbVersion == '') {
            context.error("XGBoost version cannot be read")
        }
        return xgbVersion
    }

    def readCurrentGradleVersion(final context, final h2o3Root) {
        final def gradleVersion = context.sh(script: "cd ${h2o3Root} && cat gradle/wrapper/gradle-wrapper.properties | grep distributionUrl | egrep -o '([0-9]+\\.+)+[0-9]+'", returnStdout: true).trim()
        context.echo "Gradle Version: ${gradleVersion}"
        if (!gradleVersion) {
            context.error("Gradle version cannot be read")
        }
        return gradleVersion
    }

    boolean dockerImageExistsInRegistry(final context, final String registry, final String imageName, final String version) {
        context.withCredentials([context.usernamePassword(credentialsId: "${registry}", usernameVariable: 'REGISTRY_USERNAME', passwordVariable: 'REGISTRY_PASSWORD')]) {
            final String response = "curl -k -u ${context.REGISTRY_USERNAME}:${context.REGISTRY_PASSWORD} https://${registry}/v2/${imageName}/tags/list".execute().text

            final def jsonResponse = new groovy.json.JsonSlurper().parseText(response)
            if (jsonResponse.errors) {
                for (Map error in jsonResponse.errors) {
                    if (error.code == "NAME_UNKNOWN") {
                        return false
                    }
                }
                if (jsonResponse.tags == null) {
                    context.echo "response: ${response}"
                    context.error "Docker registry check failed."
                }
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

    void unpackTestPackage(final context, final buildConfig, final String component, final String stageDir) {
        context.echo "###### Pulling test package. ######"
        context.dir(stageDir) {
            unstashFiles(context, buildConfig.getStashNameForTestPackage(component))
            unstashFiles(context, buildConfig.H2O_JAR_STASH_NAME)
        }
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
