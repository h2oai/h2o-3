H2O_HADOOP_STARTUP_MODE_HADOOP='ON_HADOOP'
H2O_HADOOP_STARTUP_MODE_HADOOP_SPNEGO='ON_HADOOP_WITH_SPNEGO'
H2O_HADOOP_STARTUP_MODE_HADOOP_HDFS_REFRESH='ON_HADOOP_WITH_HDFS_TOKEN_REFRESH'
H2O_HADOOP_STARTUP_MODE_STANDALONE='STANDALONE'
H2O_HADOOP_STARTUP_MODE_STEAM_DRIVER='STEAM_DRIVER'
H2O_HADOOP_STARTUP_MODE_STEAM_MAPPER='STEAM_MAPPER'
H2O_HADOOP_STARTUP_MODE_SPARKLING='SPARKLING'
H2O_HADOOP_STARTUP_MODE_STEAM_SPARKLING='STEAM_SPARKLING'


def call(final pipelineContext, final stageConfig) {
    withCredentials([usernamePassword(credentialsId: 'ldap-credentials', usernameVariable: 'LDAP_USERNAME', passwordVariable: 'LDAP_PASSWORD')]) {
        def commandFactory = load(stageConfig.customData.commandFactory)
        stageConfig.customBuildAction = """
            if [ -n "\$HADOOP_CONF_DIR" ]; then
                export HADOOP_CONF_DIR=\$(realpath \${HADOOP_CONF_DIR})
            fi

            . /usr/sbin/hive_version_check.sh

            echo "Activating Python ${stageConfig.pythonVersion}"
            . /envs/h2o_env_python${stageConfig.pythonVersion}/bin/activate

            echo 'Initializing Hadoop environment...'
            sudo -E /usr/sbin/startup.sh

            echo 'Generating SSL Certificate'
            rm -f mykeystore.jks
            keytool -genkey -dname "cn=Mr. Jenkins, ou=H2O-3, o=H2O.ai, c=US" -alias h2o -keystore mykeystore.jks -storepass h2oh2o -keypass h2oh2o -keyalg RSA -keysize 2048

            echo 'Building H2O'
            BUILD_HADOOP=true H2O_TARGET=${stageConfig.customData.distribution}${stageConfig.customData.version} ./gradlew clean build -x test

            echo 'Starting H2O on Hadoop'
            ${commandFactory(stageConfig)}
            if [ -z \${CLOUD_IP} ]; then
                echo "CLOUD_IP must be set"
                exit 1
            fi
            if [ -z \${CLOUD_PORT} ]; then
                echo "CLOUD_PORT must be set"
                exit 1
            fi
            echo "Cloud IP:PORT ----> \$CLOUD_IP:\$CLOUD_PORT"

            echo "Running Make"
            make -f ${pipelineContext.getBuildConfig().MAKEFILE_PATH} ${stageConfig.target}${getMakeTargetSuffix(stageConfig)} ${commandFactory(stageConfig, true)} check-leaks
        """
        
        stageConfig.postFailedBuildAction = getPostFailedBuildAction(stageConfig.customData.mode)

        def h2oFolder = stageConfig.stageDir + '/h2o-3'
        dir(h2oFolder) {
            retryWithTimeout(60, 3) {
                echo "###### Checkout H2O-3 ######"
                checkout scm
            }
        }
        
        def defaultStage = load('h2o-3/scripts/jenkins/groovy/defaultStage.groovy')
        try {
            defaultStage(pipelineContext, stageConfig)
        } finally {
            sh "find ${stageConfig.stageDir} -name 'h2odriver*.jar' -type f -delete -print"
        }
    }
}

private String getMakeTargetSuffix(final stageConfig) {
    switch (stageConfig.customData.mode) {
        case H2O_HADOOP_STARTUP_MODE_HADOOP:
        case H2O_HADOOP_STARTUP_MODE_STEAM_DRIVER:
        case H2O_HADOOP_STARTUP_MODE_STEAM_MAPPER:
        case H2O_HADOOP_STARTUP_MODE_SPARKLING:
        case H2O_HADOOP_STARTUP_MODE_STEAM_SPARKLING:
        case H2O_HADOOP_STARTUP_MODE_HADOOP_HDFS_REFRESH:
            return "-hdp"
        case H2O_HADOOP_STARTUP_MODE_HADOOP_SPNEGO:
            return "-spnego"
        case H2O_HADOOP_STARTUP_MODE_STANDALONE:
            return "-standalone"
        default:
            error("Startup mode ${stageConfig.customData.mode} for H2O with Hadoop is not supported by the makefile (cannot make Makefile target)")
    }
}

private String getPostFailedBuildAction(final mode) {
    switch (mode) {
        case H2O_HADOOP_STARTUP_MODE_HADOOP:
        case H2O_HADOOP_STARTUP_MODE_HADOOP_SPNEGO:
        case H2O_HADOOP_STARTUP_MODE_STEAM_DRIVER:
        case H2O_HADOOP_STARTUP_MODE_STEAM_MAPPER:
        case H2O_HADOOP_STARTUP_MODE_SPARKLING:
        case H2O_HADOOP_STARTUP_MODE_STEAM_SPARKLING:
            return """
                if [ -f h2o_one_node ]; then
                    export YARN_APPLICATION_ID=\$(cat h2o_one_node | grep job | sed 's/job/application/g')
                    echo "YARN Application ID is \${YARN_APPLICATION_ID}"
                    yarn application -kill \${YARN_APPLICATION_ID}
                    yarn logs -applicationId \${YARN_APPLICATION_ID} > h2o_yarn.log 
                fi   
            """
        default:
            return ""
    }
}

return this
