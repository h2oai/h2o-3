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
    
            echo 'Generating SSL Certificate'
            rm -f mykeystore.jks
            keytool -genkey -dname "cn=Mr. Jenkins, ou=H2O-3, o=H2O.ai, c=US" -alias h2o -keystore mykeystore.jks -storepass h2oh2o -keypass h2oh2o -keyalg RSA -keysize 2048
    
            echo 'Building H2O'
            BUILD_HADOOP=true H2O_TARGET=${stageConfig.customData.distribution}${stageConfig.customData.version} ./gradlew clean build -x test
    
            echo 'Starting H2O on Hadoop'
            ${startH2OScript(stageConfig.customData, branch, buildId, "main")}
            ${startH2OScript(stageConfig.customData, branch, buildId, "xgb")}
    
            echo "Prepare workdir"
            hdfs dfs -rm -r -f $workDir
            hdfs dfs -mkdir -p $workDir
            export HDFS_WORKSPACE=$workDir
    
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
    if (clusterName == "main") xgbArgs = "-use_external_xgboost"
    return """
            rm -fv ${notifyFile} ${driverLogFile}
            hdfs dfs -rm -r -f ${cloudingDir}
            echo "jenkins:${clusterName}" >> ${clusterName}.realm.properties
            export NAME_NODE=${config.nameNode}.0xdata.loc
            hadoop jar h2o-hadoop-*/h2o-${config.distribution}${config.version}-assembly/build/libs/h2odriver.jar \\
                -jobname externalxgb_${branch}_${buildId}_${clusterName} -ea \\
                -clouding_method filesystem -clouding_dir ${cloudingDir} \\
                -n 3 -mapperXmx 8G -baseport 54445 -timeout 360 \\
                -context_path ${clusterName} -hash_login -login_conf ${clusterName}.realm.properties \\
                ${xgbArgs} -notify ${notifyFile} \\
                > ${driverLogFile} 2>&1 &
            for i in \$(seq 24); do
              if [ -f '${notifyFile}' ]; then
                echo "H2O started on \$(cat ${notifyFile})"
                break
              fi
              echo "Waiting for H2O to come up (\$i)..."
              sleep 5
            done
            if [ ! -f '${notifyFile}' ]; then
              echo 'H2O failed to start!'
              cat ${driverLogFile}
              exit 1
            fi
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
        if [ -f ${notifyFile} ]; then
            YARN_APPLICATION_ID=\$(cat ${notifyFile} | grep job | sed 's/job/application/g')
        elif [ -f ${driverLogFile} ]; then
            YARN_APPLICATION_ID=\$(cat ${driverLogFile} | grep 'yarn logs -applicationId' | sed -r 's/.*(application_[0-9]+_[0-9]+).*/\\1/')
        fi
        if [ "\$YARN_APPLICATION_ID" != "" ]; then
            echo "YARN Application ID is \${YARN_APPLICATION_ID}"
            yarn application -kill \${YARN_APPLICATION_ID}
            yarn logs -applicationId \${YARN_APPLICATION_ID} > h2o_yarn.log
        else
            echo "No cleanup, did not find yarn application id."
        fi        
    """
}

return this
