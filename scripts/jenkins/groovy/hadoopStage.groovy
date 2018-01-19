def call(final pipelineContext, final stageConfig) {


    final String distribution = stageConfig.customData.distribution
    final String version = stageConfig.customData.version
    
    stageConfig.image = pipelineContext.getBuildConfig().getSmokeHadoopImage(distribution, version)
    stageConfig.customBuildAction = """
        echo "Activating Python ${stageConfig.pythonVersion}"
        . /envs/h2o_env_python${stageConfig.pythonVersion}/bin/activate
    
        echo 'Initializing Hadoop environment...'
        sudo -E /usr/sbin/startup.sh
        
        echo 'Starting H2O on Hadoop'
        hadoop jar h2o-hadoop/h2o-${distribution}${version}-assembly/build/libs/h2odriver.jar -n 1 -mapperXmx 2g -baseport 54445 -output 161_${distribution}${version}_jenkins_test -notify h2o_one_node -ea -disown
        
        IFS=":" read CLOUD_IP CLOUD_PORT < h2o_one_node
        export CLOUD_IP=\$CLOUD_IP
        export CLOUD_PORT=\$CLOUD_PORT
        echo "Cloud IP:PORT ----> \$CLOUD_IP:\$CLOUD_PORT"
        
        echo "Running Make"
        make -f ${pipelineContext.getBuildConfig().MAKEFILE_PATH} test-hadoop-smoke
    """

    def defaultStage = load('h2o-3/scripts/jenkins/groovy/defaultStage.groovy')
    defaultStage(pipelineContext, stageConfig)
}

return this
