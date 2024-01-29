def call(final pipelineContext, final Closure body) {
  final List<String> FILES_TO_EXCLUDE = [
          '**/rest.log', 
          '**/*prediction*.csv', 
          '**/java*_*.out.txt'
  ]

  final List<String> FILES_TO_ARCHIVE_ON_FAILURE = [
          '**/leak-check.out',
          '**/*.log',
          '**/out.*',
          '**/results/*.txt',
          '**/results/failed/*.txt',
          '**/results/*.code',
          '**/results/failed/*.code',
          '**/results/failed/*.code',
          '**/java*_*.out.txt.gz',
  ]

  final List<String> FILES_TO_ARCHIVE_ON_SUCCESS = [
          '**/summary.txt'
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
    def makeVars = []
    def additionalGradleOpts = pipelineContext.getBuildConfig().getAdditionalGradleOpts()
    if (additionalGradleOpts != null && !additionalGradleOpts.isEmpty()) {
      makeVars += "ADDITIONAL_GRADLE_OPTS='${pipelineContext.getBuildConfig().getAdditionalGradleOpts().join(' ')}'"
    }

    config.customBuildAction = """
      if [ "${config.activatePythonEnv}" = 'true' ]; then
        echo "Activating Python ${env.PYTHON_VERSION}"
        . /envs/h2o_env_python${env.PYTHON_VERSION}/bin/activate
      fi
      
      echo '########################'
      echo '# USING THESE VERSIONS #'
      echo '########################'
      if [ \$(command -v java) ]; then
        java -version
        javac -version
      fi
      if [ \$(command -v python) ]; then
        python --version
      fi
      if [ \$(command -v R) ]; then
        R --version
      fi

      echo "Running Make"
      export ${makeVars.join(' ')}
      make -f ${config.makefilePath} ${config.target} check-leaks
    """
  }

  boolean success = false
  if (config.preBuildAction) {
    execMake(config.preBuildAction, config.h2o3dir)
  }
  try {
    execMake(config.customBuildAction, config.h2o3dir)
    success = true
  } finally {
    if (success && config.postSuccessfulBuildAction) {
      execMake(config.postSuccessfulBuildAction, config.h2o3dir)
    }
    if (!success && config.postFailedBuildAction) {
      execMake(config.postFailedBuildAction, config.h2o3dir)
    }
    if (config.postBuildAction) {
      execMake(config.postBuildAction, config.h2o3dir)
    }
    // bigdata & smalldata are on a network mounted filesystem - we are going to do some recursive search next and 
    // we don't want to slow it down by searching on network mounts - delete the symlinks now
    cleanUpSymlinks(config.h2o3dir)
    if (config.hasJUnit) {
      final GString findCmd = "find ${config.h2o3dir} -type f -name '*.xml'"
      final GString replaceCmd = "${findCmd} -exec sed -i 's/&#[0-9]\\+;//g' {} +"
      sh replaceCmd
      pipelineContext.getUtils().archiveJUnitResults(this, config.h2o3dir)
    }
    if (config.archiveFiles) {
      execMake("make -f ${config.makefilePath} compress-huge-logfiles", config.h2o3dir)
      pipelineContext.getUtils().archiveStageFiles(this,
              config.h2o3dir,
              success ? FILES_TO_ARCHIVE_ON_SUCCESS : FILES_TO_ARCHIVE_ON_FAILURE,
              FILES_TO_EXCLUDE)
    }
    if (config.archiveAdditionalFiles) {
      echo "###### Archiving additional files: ######"
      echo "${config.archiveAdditionalFiles.join(', ')}"
      pipelineContext.getUtils().archiveStageFiles(this, config.h2o3dir, config.archiveAdditionalFiles, config.excludeAdditionalFiles)
    }
  }
}

private void execMake(final String buildAction, final String h2o3dir) {
  sh """
    export JAVA_HOME=`find /usr/lib/jvm -name '*java*${env.JAVA_VERSION}*' -type l | head -n1`
    export PATH=\${JAVA_HOME}/bin:\${PATH}

    cd ${h2o3dir}
    echo "Linking small and bigdata"
    rm -fv smalldata
    ln -s -f -v /home/0xdiag/smalldata
    rm -fv bigdata
    ln -s -f -v /home/0xdiag/bigdata

    # The Gradle fails if there is a special character, in these variables
    unset CHANGE_AUTHOR_DISPLAY_NAME
    unset CHANGE_TITLE

    printenv | sort
    ${buildAction}
  """
}

private void cleanUpSymlinks(final String h2o3dir) {
  sh """
    cd ${h2o3dir}
    echo "Unlinking small and bigdata"
    rm -fv smalldata
    rm -fv bigdata
  """
}

return this
