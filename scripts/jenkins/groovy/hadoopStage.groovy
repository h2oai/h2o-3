H2O_HADOOP_STARTUP_MODE_HADOOP='ON_HADOOP'
H2O_HADOOP_STARTUP_MODE_STANDALONE='STANDALONE'
H2O_HADOOP_STARTUP_MODE_WITH_KRB='WITH_KRB'

def call(final pipelineContext, final stageConfig) {
    withCredentials([usernamePassword(credentialsId: 'ldap-credentials', usernameVariable: 'LDAP_USERNAME', passwordVariable: 'LDAP_PASSWORD')]) {
        stageConfig.customBuildAction = """
            if [ -n "\$HADOOP_CONF_DIR" ]; then
                export HADOOP_CONF_DIR=\$(realpath \${HADOOP_CONF_DIR})
            fi

            if [ -n "\$HADOOP_DAEMON" ]; then
                export HADOOP_DAEMON=\$(realpath \${HADOOP_DAEMON})
            fi
            if [ -n "\$YARN_DAEMON" ]; then
                export YARN_DAEMON=\$(realpath \${YARN_DAEMON})
            fi

            echo "Activating Python ${stageConfig.pythonVersion}"
            . /envs/h2o_env_python${stageConfig.pythonVersion}/bin/activate

            echo 'Initializing Hadoop environment...'
            sudo -E /usr/sbin/startup.sh

            echo 'Starting H2O on Hadoop'
            ${getH2OStartupCmd(stageConfig)}
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
            make -f ${pipelineContext.getBuildConfig().MAKEFILE_PATH} test-hadoop-smoke
        """
        
        stageConfig.postFailedBuildAction = getPostFailedBuildAction(stageConfig.customData.mode)

        def defaultStage = load('h2o-3/scripts/jenkins/groovy/defaultStage.groovy')
        defaultStage(pipelineContext, stageConfig)
    }
}

/**
 * Returns the cmd used to start H2O in given mode (on Hadoop or standalone). The cmd <strong>must</strong> export
 * the CLOUD_IP and CLOUT_PORT env variables (they are checked afterwards).
 * @param stageConfig stage configuration to read mode and additional information from
 * @return the cmd used to start H2O in given mode
 */
private GString getH2OStartupCmd(final stageConfig) {
    switch (stageConfig.customData.mode) {
        case H2O_HADOOP_STARTUP_MODE_HADOOP:
            return """
                rm -f h2o_one_node h2odriver.out
                hadoop jar h2o-hadoop/h2o-${stageConfig.customData.distribution}${stageConfig.customData.version}-assembly/build/libs/h2odriver.jar -libjars "\$(cat /opt/hive-jars/hive-libjars)" -n 1 -mapperXmx 2g -baseport 54445 -notify h2o_one_node -ea -proxy -login_conf ${stageConfig.customData.ldapConfigPath} -ldap_login &> h2odriver.out &
                for i in \$(seq 20); do
                  if [ -f 'h2o_one_node' ]; then
                    echo "H2O started on \$(cat h2o_one_node)"
                    break
                  fi
                  echo "Waiting for H2O to come up (\$i)..."
                  sleep 3
                done
                if [ ! -f 'h2o_one_node' ]; then
                  echo 'H2O failed to start!'
                  cat h2odriver.out
                  exit 1
                fi
                IFS=":" read CLOUD_IP CLOUD_PORT < h2o_one_node
                export CLOUD_IP=\$CLOUD_IP
                export CLOUD_PORT=\$CLOUD_PORT
            """
        case H2O_HADOOP_STARTUP_MODE_STANDALONE:
            def defaultPort = 54321
            return """
                java -cp build/h2o.jar:\$(cat /opt/hive-jars/hive-libjars | tr ',' ':') water.H2OApp -port ${defaultPort} -ip \$(hostname --ip-address) -name \$(date +%s) >standalone_h2o.log 2>&1 & sleep 15
                export CLOUD_IP=\$(hostname --ip-address)
                export CLOUD_PORT=${defaultPort}
            """
        case H2O_HADOOP_STARTUP_MODE_WITH_KRB:
            def defaultPort = 54321
            return """
                java -cp build/h2o.jar:\$(cat /opt/hive-jars/hive-libjars | tr ',' ':') water.H2OApp -port ${defaultPort} -ip \$(hostname --ip-address) -name \$(date +%s) >standalone_h2o.log 2>&1 & sleep 15
                export CLOUD_IP=\$(hostname --ip-address)
                export CLOUD_PORT=${defaultPort}
            """
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
