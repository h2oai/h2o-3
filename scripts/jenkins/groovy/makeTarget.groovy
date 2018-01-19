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

  if (config.archiveFiles == null) {
    config.archiveFiles = true
  }

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
  } finally {
    if (config.hasJUnit) {
      final GString findCmd = "find ${config.h2o3dir} -type f -name '*.xml'"
      final GString replaceCmd = "${findCmd} -exec sed -i 's/&#[0-9]\\+;//g' {} +"
      echo "Post-processing following test result files:"
      sh findCmd
      sh replaceCmd
      junit testResults: "${config.h2o3dir}/**/test-results/*.xml", allowEmptyResults: true, keepLongStdio: true
    }
    if (config.archiveFiles) {
      archiveStageFiles(config.h2o3dir, FILES_TO_ARCHIVE, FILES_TO_EXCLUDE)
      if (config.archiveAdditionalFiles) {
        echo "###### Archiving additional files: ######"
        echo "${config.archiveAdditionalFiles}"
        archiveStageFiles(config.h2o3dir, config.archiveAdditionalFiles, config.excludeAdditionalFiles)
      }
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
  archiveArtifacts artifacts: archiveFiles.collect{"${h2o3dir}/${it}"}.join(', '), allowEmptyArchive: true, excludes: excludeFiles.collect{"${h2o3dir}/${it}"}.join(', ')
}

return this
