def call(final pipelineContext, final stageConfig) {
    def branch = env.BRANCH_NAME.replaceAll("\\/", "-")
    def workDir = "/user/jenkins/workspaces/$branch"
    withCredentials([usernamePassword(credentialsId: 'mr-0xd-admin-credentials', usernameVariable: 'ADMIN_USERNAME', passwordVariable: 'ADMIN_PASSWORD')]) {
        stageConfig.customBuildAction = """
            export HADOOP_CONF_DIR=/etc/hadoop/conf/
            ${downloadConfigsScript()}
    
            echo "Activating Python ${stageConfig.pythonVersion}"
            . /envs/h2o_env_python${stageConfig.pythonVersion}/bin/activate
    
            echo 'Generating SSL Certificate'
            rm -f mykeystore.jks
            keytool -genkey -dname "cn=Mr. Jenkins, ou=H2O-3, o=H2O.ai, c=US" -alias h2o -keystore mykeystore.jks -storepass h2oh2o -keypass h2oh2o -keyalg RSA -keysize 2048
    
            echo 'Building H2O'
            BUILD_HADOOP=true H2O_TARGET=${stageConfig.customData.distribution}${stageConfig.customData.version} ./gradlew clean build -x test
    
            echo 'Starting H2O on Hadoop'
            ${startH2OScript(stageConfig, branch)}
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

private GString downloadConfigsScript() {
    def apiBase = "http://mr-0xd6.0xdata.loc:8080/api/v1/clusters/hdp2_2_d/services"
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

private GString startH2OScript(final stageConfig, final branch) {
    def nodes = stageConfig.customData.nodes
    def xmx = stageConfig.customData.xmx
    def extraMem = stageConfig.customData.extramem
    def cloudingDir = stageConfig.customData.clouding_dir + "-" + branch
    return """
            rm -fv h2o_one_node h2odriver.log
            hdfs dfs -rm -r -f ${cloudingDir}
            export NAME_NODE=${stageConfig.customData.name_node}
            hadoop jar h2o-hadoop-*/h2o-${stageConfig.customData.distribution}${stageConfig.customData.version}-assembly/build/libs/h2odriver.jar \\
                -disable_flow -ea \\
                -clouding_method filesystem -clouding_dir ${cloudingDir} \\
                -n ${nodes} -mapperXmx ${xmx} -extramempercent ${extraMem} -baseport 54445 -timeout 360 \\
                -notify h2o_one_node \\
                > h2odriver.log 2>&1 &
            for i in \$(seq 12); do
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
            export YARN_APPLICATION_ID=\$(cat h2o_one_node | grep job | sed 's/job/application/g')
            echo "YARN Application ID is \${YARN_APPLICATION_ID}"
            yarn application -kill \${YARN_APPLICATION_ID}
            yarn logs -applicationId \${YARN_APPLICATION_ID} > h2o_yarn.log
        fi
    """
}

return this
