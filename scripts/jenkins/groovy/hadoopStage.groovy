def call(buildConfig, stageConfig) {

    def defaultStage = load('h2o-3/scripts/jenkins/groovy/defaultStage.groovy')

    stageConfig.image = buildConfig.getSmokeHadoopImage(stageConfig.distribution, stageConfig.version)
    stageConfig.customBuildAction = """
        echo "Activating Python ${stageConfig.pythonVersion}"
        . /envs/h2o_env_python${stageConfig.pythonVersion}/bin/activate
    
        echo 'Initializing Hadoop environment...'
        sudo /usr/sbin/startup.sh
        
        echo 'Starting H2O on Hadoop'
        hadoop jar h2o-hadoop/h2o-${stageConfig.distribution}${stageConfig.version}-assembly/build/libs/h2odriver.jar -n 1 -mapperXmx 2g -baseport 54445 -output 161_${stageConfig.distribution}${stageConfig.version}_jenkins_test -notify h2o_one_node -ea -disown
        
        IFS=":" read CLOUD_IP CLOUD_PORT < h2o_one_node
        export CLOUD_IP=\$CLOUD_IP
        export CLOUD_PORT=\$CLOUD_PORT
        echo "Cloud IP:PORT ----> \$CLOUD_IP:\$CLOUD_PORT"
        
        echo "Running Make"
        make -f docker/Makefile.jenkins test-hadoop-smoke
    """
    //FIXME
    stageConfig.nodeLabel = 'mr-0xg4'

    defaultStage(buildConfig, stageConfig)
}

return this
