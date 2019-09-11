def call(final pipelineContext, final stageConfig) {
  
  def insideDocker = load('h2o-3/scripts/jenkins/groovy/insideDocker.groovy')

  final String DATASETS_FILE = 'accuracy_datasets_h2o.csv'
  final GString H2O_ROOT = "${env.WORKSPACE}/${pipelineContext.getUtils().stageNameToDirName(stageConfig.stageName)}/h2o-3"
  final GString ML_BENCHMARK_ROOT = "${H2O_ROOT}/ml-benchmark"

  stageConfig.datasetsPath = "${ML_BENCHMARK_ROOT}/jenkins/${DATASETS_FILE}"
  stageConfig.makefilePath = stageConfig.makefilePath ?: "${ML_BENCHMARK_ROOT}/jenkins/Makefile.jenkins"

  dir (ML_BENCHMARK_ROOT) {
    retry(3) {
      timeout(time: 1, unit: 'MINUTES') {
        checkout changelog: false, poll: false, scm: [$class: 'GitSCM', branches: [[name: 'master']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: pipelineContext.getBuildConfig().H2O_OPS_CREDS_ID, url: 'https://github.com/h2oai/ml-benchmark']]]
      }
    }
  }

  def prepareBenchmarkFolderConfig = pipelineContext.getPrepareBenchmarkDirStruct(this, ML_BENCHMARK_ROOT)
  def benchmarkFolderConfig = prepareBenchmarkFolderConfig(stageConfig.customData.algorithm, env.GIT_SHA, env.BRANCH_NAME)
  GString outputPath = "${env.workspace}/${pipelineContext.getUtils().stageNameToDirName(stageConfig.stageName)}/${benchmarkFolderConfig.getOutputDir()}"
  sh "rm -rf ${outputPath} && mkdir -p ${outputPath}"
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

        export PATH_PREFIX=${ML_BENCHMARK_ROOT}
        export DATASETS_PATH=${stageConfig.datasetsPath}
        export TEST_CASES_PATH=${stageConfig.testCasesPath}
        export OUTPUT_PATH=${outputPath}
        export GIT_SHA=${env.GIT_SHA}
        export GIT_DATE=${env.GIT_DATE.replaceAll(' ', '-')}
        export BENCHMARK_ALGORITHM=${stageConfig.customData.algorithm}
        export BUILD_ID=${env.BUILD_ID}
        export H2O_JAR_PATH=${H2O_ROOT}/build/h2o.jar
        
        echo "Running Make"
        
        make -f ${stageConfig.makefilePath} ${stageConfig.target}
    """

  try {
    sh "nvidia-docker run --rm --init --pid host -u \$(id -u):\$(id -g) -v ${WORKSPACE}:${WORKSPACE} -v /home/0xdiag/:/home/0xdiag ${stageConfig.image} bash -c '''${stageConfig.customBuildAction}'''"
  } finally {
    insideDocker(benchmarkEnv, pipelineContext.getBuildConfig().S3CMD_IMAGE, pipelineContext.getBuildConfig().DOCKER_REGISTRY, pipelineContext.getBuildConfig(), 5, 'MINUTES') {
      def persistBenchmarkResults = load("${ML_BENCHMARK_ROOT}/jenkins/groovy/persistBenchmarkResults.groovy")
      persistBenchmarkResults(benchmarkFolderConfig, pipelineContext.getUtils().stageNameToDirName(stageConfig.stageName))
    }
  }

  def compareBenchmarksStage = load("h2o-3/scripts/jenkins/groovy/compareBenchmarksStage.groovy")
  compareBenchmarksStage(pipelineContext, stageConfig, benchmarkFolderConfig)
}

return this
