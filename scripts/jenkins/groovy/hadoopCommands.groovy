/**
 * Returns the cmd used to start H2O in given mode (on Hadoop or standalone). The cmd <strong>must</strong> export
 * the CLOUD_IP and CLOUT_PORT env variables (they are checked afterwards).
 * @param stageConfig stage configuration to read mode and additional information from
 * @return the cmd used to start H2O in given mode
 */
def call(final stageConfig) {
    switch (stageConfig.customData.mode) {
        case H2O_HADOOP_STARTUP_MODE_HADOOP:
            return getCommandHadoop(stageConfig)
        case H2O_HADOOP_STARTUP_MODE_STANDALONE:
            return getCommandStandalone(stageConfig)
        default:
            error("Startup mode ${stageConfig.customData.mode} for H2O with Hadoop is not supported")
    }
}

private GString getCommandHadoop(final stageConfig) {
    return """
            rm -fv h2o_one_node h2odriver.log
            hadoop jar h2o-hadoop-*/h2o-${stageConfig.customData.distribution}${stageConfig.customData.version}-assembly/build/libs/h2odriver.jar \\
                -disable_flow \\
                -n 1 -mapperXmx 2g -baseport 54445 \\
                -internal_secure_connections \\
                -jks mykeystore.jks \\
                -notify h2o_one_node -ea -proxy \\
                -jks mykeystore.jks \\
                -login_conf ${stageConfig.customData.ldapConfigPath} -ldap_login \\
                > h2odriver.log 2>&1 &
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
              cat h2odriver.log
              exit 1
            fi
            IFS=":" read CLOUD_IP CLOUD_PORT < h2o_one_node
            export CLOUD_IP=\$CLOUD_IP
            export CLOUD_PORT=\$CLOUD_PORT
        """
}

private GString getCommandStandalone(final stageConfig) {
    def defaultPort = 54321
    return """
            java -cp build/h2o.jar:\$(cat /opt/hive-jdbc-cp) water.H2OApp \\
                -port ${defaultPort} -ip \$(hostname --ip-address) -name \$(date +%s) \\
                -jks mykeystore.jks \\
                -login_conf ${stageConfig.customData.ldapConfigPathStandalone} -ldap_login \\
                > standalone_h2o.log 2>&1 & sleep 15
            export CLOUD_IP=\$(hostname --ip-address)
            export CLOUD_PORT=${defaultPort}
            """
}

return this
