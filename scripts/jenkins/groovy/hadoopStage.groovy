H2O_HADOOP_STARTUP_MODE_HADOOP='ON_HADOOP'
H2O_HADOOP_STARTUP_MODE_STANDALONE='STANDALONE'

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
            make -f ${pipelineContext.getBuildConfig().MAKEFILE_PATH} ${stageConfig.target}${getMakeTargetSuffix(stageConfig)} check-leaks
        """
        
        stageConfig.postFailedBuildAction = getPostFailedBuildAction(stageConfig.customData.mode)

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
            return "-hdp"
        case H2O_HADOOP_STARTUP_MODE_STANDALONE:
            return "-standalone"
        default:
            error("Startup mode ${stageConfig.customData.mode} for H2O with Hadoop is not supported")
    }
}


private String getPostFailedBuildAction(final mode) {
    switch (mode) {
        case H2O_HADOOP_STARTUP_MODE_HADOOP:
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
