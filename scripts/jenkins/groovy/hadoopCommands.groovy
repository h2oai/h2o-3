/**
 * Returns the cmd used to start H2O in given mode (on Hadoop or standalone). The cmd <strong>must</strong> export
 * the CLOUD_IP and CLOUT_PORT env variables (they are checked afterwards).
 * @param stageConfig stage configuration to read mode and additional information from
 * @return the cmd used to start H2O in given mode
 */
def call(final stageConfig, final boolean getMakeTarget = false) {
    if (getMakeTarget) {
        return ""
    }
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
    def authConf = stageConfig.customData.customAuth ?: "-login_conf ${stageConfig.customData.ldapConfigPath} -ldap_login"
    return """
            rm -fv h2o_one_node h2odriver.log
            hadoop jar h2o-hadoop-*/h2o-${stageConfig.customData.distribution}${stageConfig.customData.version}-assembly/build/libs/h2odriver.jar \\
                -disable_flow \\
                -n 1 -mapperXmx 2g -baseport 54445 -timeout 300 \\
                -internal_secure_connections -allow_insecure_xgboost \\
                -jks mykeystore.jks \\
                -notify h2o_one_node \\
                -flatfile flatfile.lst \\
                -JJ -Daws.accessKeyId=\$AWS_ACCESS_KEY_ID -JJ -Daws.secretKey=\$AWS_SECRET_ACCESS_KEY \\
                -ea -proxy \\
                -form_auth \\
                -jks mykeystore.jks \\
                ${authConf} \\
                > h2odriver.log 2>&1 &
            for i in \$(seq 20); do
              if [ -f 'h2o_one_node' ]; then
                echo "H2O started on \$(cat h2o_one_node)"
                break
              fi
              echo "Waiting for H2O to come up (\$i)..."
              sleep 15
            done
            if [ ! -f 'h2o_one_node' ]; then
              echo 'H2O failed to start!'
              cat h2odriver.log
              \$(grep -F 'yarn logs -applicationId' h2odriver.log | head -1 | cut -d"'" -f2)
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
                -form_auth \\
                > standalone_h2o.log 2>&1 & 
            for i in \$(seq 4); do
              if grep "Open H2O Flow in your web browser" standalone_h2o.log
              then
                echo "H2O started"
                touch standalone_start_check_success
                break
              fi
              echo "Waiting for H2O to come up (\$i)..."
              sleep 5
            done
            if [ ! -f 'standalone_start_check_success' ]; then
              echo 'H2O failed to start!'
              cat standalone_h2o.log
              exit 1
            fi
            export CLOUD_IP=\$(hostname --ip-address)
            export CLOUD_PORT=${defaultPort}
            export HADOOP_S3_FILESYSTEMS=${stageConfig.customData.bundledS3FileSystems}
            """
}

return this
