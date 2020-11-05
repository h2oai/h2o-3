def call(final pipelineContext, final stageConfig) {
    def branch = env.BRANCH_NAME.replaceAll("\\/", "-")
    def buildId = env.BUILD_ID
    def workDir = "/user/jenkins/workspaces/xgb-$branch"
    withCredentials([
            usernamePassword(credentialsId: 'mr-0xd-admin-credentials', usernameVariable: 'ADMIN_USERNAME', passwordVariable: 'ADMIN_PASSWORD')
    ]) {
        stageConfig.customBuildAction = """
            export HADOOP_CONF_DIR=/etc/hadoop/conf/
            ${downloadConfigsScript(stageConfig.customData)}
    
            echo "Activating Python ${stageConfig.pythonVersion}"
            . /envs/h2o_env_python${stageConfig.pythonVersion}/bin/activate
            pip install websocket_client
    
            echo 'Building H2O'
            BUILD_HADOOP=true H2O_TARGET=${stageConfig.customData.distribution}${stageConfig.customData.version} ./gradlew clean build -x test
    
            echo 'Starting H2O on Hadoop'
            ${startH2OScript(stageConfig.customData, branch, buildId, "main")}
            ${startH2OScript(stageConfig.customData, branch, buildId, "xgb")}
    
            echo "Prepare workdir"
            hdfs dfs -rm -r -f $workDir
            hdfs dfs -mkdir -p $workDir
            export HDFS_WORKSPACE=$workDir
            export NAME_NODE=${stageConfig.customData.nameNode}.0xdata.loc
    
            echo "Running Test"
            make -f ${pipelineContext.getBuildConfig().MAKEFILE_PATH} ${stageConfig.target}
        """

        def killScript =  getKillScript("main") + getKillScript("xgb")
        stageConfig.postFailedBuildAction = killScript
        stageConfig.postSuccessfulBuildAction = killScript
    
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

private GString downloadConfigsScript(Map config) {
    def apiBase = "http://${config.nameNode}.0xdata.loc:8080/api/v1/clusters/${config.hdpName}/services"
    return """
        echo "Downloading hadoop configuration from ${apiBase}"
        cd \$HADOOP_CONF_DIR
        curl -u \$ADMIN_USERNAME:\$ADMIN_PASSWORD ${apiBase}/HDFS/components/HDFS_CLIENT?format=client_config_tar > hdfs_config.tar
        tar xvvf hdfs_config.tar
        curl -u \$ADMIN_USERNAME:\$ADMIN_PASSWORD ${apiBase}/MAPREDUCE2/components/MAPREDUCE2_CLIENT?format=client_config_tar > mapred_config.tar
        tar xvvf mapred_config.tar
        curl -u \$ADMIN_USERNAME:\$ADMIN_PASSWORD ${apiBase}/YARN/components/YARN_CLIENT?format=client_config_tar > yarn_config.tar
        tar xvvf yarn_config.tar
        rm *.tar
        cd -
    """
}

private GString startH2OScript(final config, final branch, final buildId, final clusterName) {
    def cloudingDir = config.cloudingDir + branch + "-ext-xgb-" + clusterName
    def notifyFile = "h2o_notify_${clusterName}"
    def driverLogFile = "h2odriver_${clusterName}.log"
    def xgbArgs = ""
    if (clusterName == "main") xgbArgs = "--use-external-xgb"
    return """
            scripts/jenkins/hadoop/start.sh \\
                --cluster-name ${clusterName} \\
                --clouding-dir ${cloudingDir} \\
                --notify-file ${notifyFile} \\
                --driver-log-file ${driverLogFile} \\
                --hadoop-version ${config.distribution}${config.version} \\
                --job-name externalxgb_${branch}_${buildId}_${clusterName} \\
                --nodes 3 --xmx 8G --extra-mem 20 \\
                --context-path ${clusterName} \\
                --enable-login \\
                ${xgbArgs}
            IFS=":" read CLOUD_IP CLOUD_PORT < ${notifyFile}
            if [ -z \${CLOUD_IP} ]; then
                echo "CLOUD_IP must be set"
                exit 1
            fi
            if [ -z \${CLOUD_PORT} ]; then
                echo "CLOUD_PORT must be set"
                exit 1
            fi
            export cloud_ip_port_${clusterName}=\${CLOUD_IP}:\${CLOUD_PORT}
            echo "Cloud ${clusterName} IP:PORT ----> \$cloud_ip_port_${clusterName}"
        """
}

private String getKillScript(final clusterName) {
    def notifyFile = "h2o_notify_${clusterName}"
    def driverLogFile = "h2odriver_${clusterName}.log"
    return """
        scripts/jenkins/hadoop/kill.sh \\
            --notify-file ${notifyFile} \\
            --driver-log-file ${driverLogFile}
    """
}

return this
