def call(final pipelineContext, final stageConfig) {
    final List<String> FILES_TO_EXCLUDE = [
            '**/rest.log'
    ]

    final List<String> FILES_TO_ARCHIVE = [
            "**/*.log", "**/out.*", "**/*py.out.txt", "**/java*out.txt", "**/*ipynb.out.txt",
            "**/results/*", "**/*tmp_model*",
            "**/h2o-py/tests/testdir_dynamic_tests/testdir_algos/glm/Rsandbox*/*.csv",
            "**/tests.txt", "**/*lib_h2o-flow_build_js_headless-test.js.out.txt",
            "**/*.code", "**/package_version_check_out.txt"
    ]

    for (String component : stageConfig.additionalTestPackages) {
        pipelineContext.getUtils().unpackTestPackage(this, pipelineContext.getBuildConfig(), component, stageConfig.stageDir)
    }

    final String h2o3dir = "${stageConfig.stageDir}/h2o-3"

    stageConfig.customBuildAction = """
        export HOME=/home/jenkins
        export USER=jenkins
        export GRADLE_USER_HOME='/home/jenkins/.gradle'

        export PATH=/usr/lib/jvm/java-8-oracle/bin:/bin:/usr/local/nvidia/bin:/usr/local/cuda/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
        
        locale
        env
        
        cd ${WORKSPACE}/${h2o3dir}
        
        pwd
        ls -alh
        
        echo "Linking small and bigdata"
        rm -fv smalldata
        ln -s -f -v /home/0xdiag/smalldata
        rm -fv bigdata
        ln -s -f -v /home/0xdiag/bigdata
        
        if [ "${stageConfig.activatePythonEnv}" = 'true' ]; then
            echo "Activating Python ${stageConfig.pythonVersion}"
            . /envs/h2o_env_python${stageConfig.pythonVersion}/bin/activate
        fi
        
        echo "Running Make"
        
        unset COMMIT_MESSAGE
        unset CHANGE_AUTHOR_DISPLAY_NAME
        unset CHANGE_TITLE
        unset COMMIT_MESSAGE
        
        make -f ${stageConfig.makefilePath} ${stageConfig.target}
    """
    try {
        sh "nvidia-docker run --rm --init --pid host -u \$(id -u):\$(id -g) -v ${WORKSPACE}:${WORKSPACE} -v /home/0xdiag/:/home/0xdiag ${stageConfig.image} bash -c '''${stageConfig.customBuildAction}'''"
    } finally {
        pipelineContext.getUtils().archiveJUnitResults(this, h2o3dir)
        pipelineContext.getUtils().archiveStageFiles(this, h2o3dir, FILES_TO_ARCHIVE, FILES_TO_EXCLUDE)
    }
}

return this
