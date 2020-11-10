def call(final pipelineContext, final stageConfig) {
    def branch = env.BRANCH_NAME.replaceAll("\\/", "-")
    def buildId = env.BUILD_ID
    def workDir = "/user/jenkins/workspaces/fault-tolerance-$branch"
    withCredentials([
            usernamePassword(credentialsId: 'mr-0xd-admin-credentials', usernameVariable: 'ADMIN_USERNAME', passwordVariable: 'ADMIN_PASSWORD')
    ]) {
        stageConfig.customBuildAction = """
            export HADOOP_CONF_DIR=/etc/hadoop/conf/
            ${downloadConfigsScript(stageConfig.customData)}
    
            echo "Activating Python ${stageConfig.pythonVersion}"
            . /envs/h2o_env_python${stageConfig.pythonVersion}/bin/activate
    
            echo 'Building H2O'
            BUILD_HADOOP=true H2O_TARGET=${stageConfig.customData.distribution}${stageConfig.customData.version} ./gradlew clean build -x test
    
            echo "Prepare workdir"
            hdfs dfs -rm -r -f $workDir
            hdfs dfs -mkdir -p $workDir
            export HDFS_WORKSPACE=$workDir
            export NAME_NODE=${stageConfig.customData.nameNode}.0xdata.loc
            export H2O_HOME=\$(pwd)
            export H2O_START_SCRIPT=scripts/jenkins/hadoop/start.sh
            export H2O_KILL_SCRIPT=scripts/jenkins/hadoop/kill.sh
            export H2O_HADOOP=${stageConfig.customData.distribution}${stageConfig.customData.version}
            export H2O_JOB_NAME=fault_tolerance_${branch}_${buildId}

            echo "Running Test"
            make -f ${pipelineContext.getBuildConfig().MAKEFILE_PATH} ${stageConfig.target}
        """

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

return this
