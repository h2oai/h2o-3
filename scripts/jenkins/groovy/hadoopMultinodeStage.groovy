def call(final pipelineContext, final stageConfig) {
    def branch = env.BRANCH_NAME.replaceAll("\\/", "-")
    def buildId = env.BUILD_ID
    def workDir = "/user/jenkins/workspaces/$branch"
    withCredentials([
            usernamePassword(credentialsId: 'mr-0xd-admin-credentials', usernameVariable: 'ADMIN_USERNAME', passwordVariable: 'ADMIN_PASSWORD'),
            usernamePassword(credentialsId: 'kerberos-credentials', usernameVariable: 'KRB_USERNAME', passwordVariable: 'KRB_PASSWORD')
    ]) {
        stageConfig.customBuildAction = """
            export HADOOP_CONF_DIR=/etc/hadoop/conf/
            ${downloadConfigsScript(stageConfig.customData)}
    
            echo "Activating Python ${stageConfig.pythonVersion}"
            . /envs/h2o_env_python${stageConfig.pythonVersion}/bin/activate
    
            echo 'Generating SSL Certificate'
            rm -f mykeystore.jks
            keytool -genkey -dname "cn=Mr. Jenkins, ou=H2O-3, o=H2O.ai, c=US" -alias h2o -keystore mykeystore.jks -storepass h2oh2o -keypass h2oh2o -keyalg RSA -keysize 2048
    
            echo 'Building H2O'
            BUILD_HADOOP=true H2O_TARGET=${stageConfig.customData.distribution}${stageConfig.customData.version} ./gradlew clean build -x test
    
            echo 'Starting H2O on Hadoop'
            ${startH2OScript(stageConfig.customData, branch, buildId)}
            if [ -z \${CLOUD_IP} ]; then
                echo "CLOUD_IP must be set"
                exit 1
            fi
            if [ -z \${CLOUD_PORT} ]; then
                echo "CLOUD_PORT must be set"
                exit 1
            fi
            echo "Cloud IP:PORT ----> \$CLOUD_IP:\$CLOUD_PORT"
    
            echo "Prepare workdir"
            hdfs dfs -rm -r -f $workDir
            hdfs dfs -mkdir -p $workDir
            export HDFS_WORKSPACE=$workDir
    
            echo "Running Make"
            make -f ${pipelineContext.getBuildConfig().MAKEFILE_PATH} ${stageConfig.target} check-leaks
        """

        stageConfig.postFailedBuildAction = getKillScript()
        stageConfig.postSuccessfulBuildAction = getKillScript()
    
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
    def krbScript = ""
    if (config.krb) {
        krbScript = """
            curl -u \$ADMIN_USERNAME:\$ADMIN_PASSWORD ${apiBase}/KERBEROS/components/KERBEROS_CLIENT?format=client_config_tar > krb_config.tar
            tar xvvf krb_config.tar
            echo "\$KRB_PASSWORD" | kinit \$KRB_USERNAME
        """
    }
    def extraInit = ""
    if (config.nameNode == "mr-0xg5") {
        extraInit = """
            export HDP_VERSION=2.4.2.0-258
        """
    }
    return """
        echo "Downloading hadoop configuration from ${apiBase}"
        cd \$HADOOP_CONF_DIR
        curl -u \$ADMIN_USERNAME:\$ADMIN_PASSWORD ${apiBase}/HDFS/components/HDFS_CLIENT?format=client_config_tar > hdfs_config.tar
        tar xvvf hdfs_config.tar
        curl -u \$ADMIN_USERNAME:\$ADMIN_PASSWORD ${apiBase}/MAPREDUCE2/components/MAPREDUCE2_CLIENT?format=client_config_tar > mapred_config.tar
        tar xvvf mapred_config.tar
        curl -u \$ADMIN_USERNAME:\$ADMIN_PASSWORD ${apiBase}/YARN/components/YARN_CLIENT?format=client_config_tar > yarn_config.tar
        tar xvvf yarn_config.tar
        ${krbScript}
        rm *.tar
        ${extraInit}
        cd -
    """
}

private GString startH2OScript(final config, final branch, final buildId) {
    def nodes = config.nodes
    def xmx = config.xmx
    def extraMem = config.extramem
    def cloudingDir = config.cloudingDir + "-" + branch
    def krbArgs = ""
    if (config.krb) {
        krbArgs = "-hiveHost ${config.hiveHost}:10000 -hivePrincipal ${config.hivePrincipal} -refreshTokens"
        krbArgs += " -internal_secure_connections -allow_insecure_xgboost"
    }
    return """
            rm -fv h2o_one_node h2odriver.log
            hdfs dfs -rm -r -f ${cloudingDir}
            export NAME_NODE=${config.nameNode}.0xdata.loc
            export HIVE_HOST=${config.hiveHost}
            HIVE_JDBC_JAR=\$(find /usr/hdp/current/hive-client/lib/ | grep -E 'jdbc.*standalone.*jar')
            export HADOOP_CLASSPATH=\$HIVE_JDBC_JAR
            hadoop jar h2o-hadoop-*/h2o-${config.distribution}${config.version}-assembly/build/libs/h2odriver.jar \\
                -libjars \$HIVE_JDBC_JAR \\
                -jobname multinode_${branch}_${buildId} \\
                -disable_flow -ea ${krbArgs} \\
                -clouding_method filesystem -clouding_dir ${cloudingDir} \\
                -n ${nodes} -mapperXmx ${xmx} -extramempercent ${extraMem} -baseport 54445 -timeout 360 \\
                -notify h2o_one_node \\
                > h2odriver.log 2>&1 &
            for i in \$(seq 24); do
              if [ -f 'h2o_one_node' ]; then
                echo "H2O started on \$(cat h2o_one_node)"
                break
              fi
              echo "Waiting for H2O to come up (\$i)..."
              sleep 5
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

private String getKillScript() {
    return """
        if [ -f h2o_one_node ]; then
            YARN_APPLICATION_ID=\$(cat h2o_one_node | grep job | sed 's/job/application/g')
        elif [ -f h2odriver.log ]; then
            YARN_APPLICATION_ID=\$(cat h2odriver.log | grep 'yarn logs -applicationId' | sed -r 's/.*(application_[0-9]+_[0-9]+).*/\\1/' | head -n 1)
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
