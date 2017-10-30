def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  def FILES_TO_ARCHIVE = [
    "**/*.log", "**/out.*", "**/*py.out.txt", "**/java*out.txt", "**/*ipynb.out.txt",
    "**/results/*", "**/*tmp_model*",
    "**/h2o-py/tests/testdir_dynamic_tests/testdir_algos/glm/Rsandbox*/*.csv",
    "**/tests.txt", "**/*lib_h2o-flow_build_js_headless-test.js.out.txt",
    "**/*.code", "**/package_version_check_out.txt"
  ]

  if (config.archiveFiles == null) {
    config.archiveFiles = true
  }

  if (config.hasJUnit == null) {
    config.hasJUnit = true
  }

  if (config.h2o3dir == null) {
    config.h2o3dir = 'h2o-3'
  }

  try {
    execMake(config.target, config.h2o3dir)
  } finally {
    if (config.hasJUnit) {
      junit testResults: "${config.h2o3dir}/**/test-results/*.xml", allowEmptyResults: true, keepLongStdio: true
    }
    if (config.archiveFiles) {
      archiveArtifacts artifacts: FILES_TO_ARCHIVE.collect{"${config.h2o3dir}/${it}"}.join(', '), allowEmptyArchive: true
    }
  }
}

def execMake(target, String h2o3dir) {
  sh """
    export JAVA_HOME=/usr/lib/jvm/java-8-oracle
    export LANG=C.UTF-8
    locale

    echo "Activating Python ${env.PYTHON_VERSION}"
    . /envs/h2o_env_python${env.PYTHON_VERSION}/bin/activate

    echo "Activating R ${env.R_VERSION}"
    activate_R_${env.R_VERSION}

    cd ${h2o3dir}
    echo "Linking small and bigdata"
    rm -f smalldata
    ln -s -f /home/0xdiag/smalldata
    rm -f bigdata
    ln -s -f /home/0xdiag/bigdata

    # The Gradle fails if there is a special character, in these variables
    unset CHANGE_AUTHOR_DISPLAY_NAME
    unset CHANGE_TITLE

    echo "Running Make"
    make -f docker/Makefile.jenkins ${target}
  """
}

return this
