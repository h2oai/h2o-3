def call(body) {
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

  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  if (config.hasJUnit == null) {
    config.hasJUnit = true
  }
  config.h2o3dir = config.h2o3dir ?: 'h2o-3'

  if (config.customBuildAction == null) {
    config.customBuildAction = """
      echo "Activating Python ${env.PYTHON_VERSION}"
      . /envs/h2o_env_python${env.PYTHON_VERSION}/bin/activate

      echo "Activating R ${env.R_VERSION}"
      activate_R_${env.R_VERSION}

      echo "Running Make"
      make -f ${config.makefilePath} ${config.target}
    """
  }

  try {
    execMake(config.customBuildAction, config.h2o3dir)
  } catch (Exception e) {
    if (config.hasJUnit) {
      final String safeJobName = env.JOB_NAME.replace('/', '_')
      final String timestamp = sh(script: 'date +%s', returnStdout: true).trim()
      // FIXME set correct s3 root
      final String s3root = 's3://test.0xdata.com/intermittents/jenkins/' // 's3://ai.h2o.tests/jenkins/'
      final String intermittentsOutputFile = "Failed_PyUnits_from_${safeJobName}.csv"
      final String intermittentsOutputDict = "Failed_PyUnits_summary_dict_from_${safeJobName}.txt"
      final String awsName = "${s3root}${intermittentsOutputFile}"
      final String awsDictName = "${s3root}${intermittentsOutputDict}"
      final String dailyOutputFile = "Daily_PyUnits_Failed_from_${safeJobName}\"_\"${timestamp}.csv"
      sh """
        cd ${config.h2o3dir}/scripts

        echo "*********************************************"
        echo "***  PostBuild: Looking for intermittents ***"
        echo "*********************************************"
        
        echo "Output file name will be ${intermittentsOutputFile}"
        
        rm -f ${intermittentsOutputFile}
        rm -f ${intermittentsOutputDict}
        
        if [ "\$(s3cmd ls ${awsName} | grep ${awsName})" ]; then
          s3cmd get ${awsName}
        fi
        if [ "\$(s3cmd ls ${awsDictName} | grep ${awsDictName})" ]; then
          s3cmd get ${awsDictName}
        fi

        # FIXME this should be already installed in system-wide python2
        virtualenv --python=python2 ~/env
        . ~/env/bin/activate
        pip install pytz python-dateutil

        python --version
        python scrapeForIntermittents.py ${timestamp} ${env.JOB_NAME} ${env.BUILD_ID} ${env.GIT_SHA} ${env.NODE_NAME} PyUnit ${env.JENKINS_URL} ${intermittentsOutputFile} ${intermittentsOutputDict} 2 ${dailyOutputFile}
        
        s3cmd put ${intermittentsOutputFile} ${s3root}
        s3cmd put ${intermittentsOutputDict} ${s3root}
        s3cmd put ${dailyOutputFile} ${s3root}
        
        rm -f Failed_*
        rm -f Daily_*
        rm -f tempText
      """
    }
    throw e
  } finally {
    if (config.archiveFiles) {
      archiveStageFiles(config.h2o3dir, FILES_TO_ARCHIVE, FILES_TO_EXCLUDE)
    }
    if (config.archiveAdditionalFiles) {
      echo "###### Archiving additional files: ######"
      echo "${config.archiveAdditionalFiles.join(', ')}"
      archiveStageFiles(config.h2o3dir, config.archiveAdditionalFiles, config.excludeAdditionalFiles)
    }
    if (config.hasJUnit) {
      final GString findCmd = "find ${config.h2o3dir} -type f -name '*.xml'"
      final GString replaceCmd = "${findCmd} -exec sed -i 's/&#[0-9]\\+;//g' {} +"
      echo "Post-processing following test result files:"
      sh findCmd
      sh replaceCmd
      junit testResults: "${config.h2o3dir}/**/test-results/*.xml", allowEmptyResults: false, keepLongStdio: true
    }
  }
}

private void execMake(final String buildAction, final String h2o3dir) {
  sh """
    export JAVA_HOME=/usr/lib/jvm/java-8-oracle

    cd ${h2o3dir}
    echo "Linking small and bigdata"
    rm -f smalldata
    ln -s -f /home/0xdiag/smalldata
    rm -f bigdata
    ln -s -f /home/0xdiag/bigdata

    # The Gradle fails if there is a special character, in these variables
    unset CHANGE_AUTHOR_DISPLAY_NAME
    unset CHANGE_TITLE

    printenv
    ${buildAction}
  """
}

private void archiveStageFiles(final String h2o3dir, final List<String> archiveFiles, final List<String> excludeFiles) {
  List<String> excludes = []
  if (excludeFiles != null) {
    excludes = excludeFiles
  }
  archiveArtifacts artifacts: archiveFiles.collect{"${h2o3dir}/${it}"}.join(', '), allowEmptyArchive: true, excludes: excludes.collect{"${h2o3dir}/${it}"}.join(', ')
}

return this
