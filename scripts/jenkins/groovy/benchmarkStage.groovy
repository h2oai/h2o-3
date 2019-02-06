def call(final pipelineContext, final stageConfig) {

  def defaultStage = load('h2o-3/scripts/jenkins/groovy/defaultStage.groovy')
  def insideDocker = load('h2o-3/scripts/jenkins/groovy/insideDocker.groovy')

  final String DATASETS_FILE = 'accuracy_datasets_h2o.csv'
  final GString TEST_CASES_FILE = "test_cases_${stageConfig.customData.algorithm}.csv"
  final GString H2O_ROOT = "${env.WORKSPACE}/${pipelineContext.getUtils().stageNameToDirName(stageConfig.stageName)}/h2o-3"
  final GString ML_BENCHMARK_ROOT = "${H2O_ROOT}/ml-benchmark"

  stageConfig.datasetsPath = "${ML_BENCHMARK_ROOT}/jenkins/${DATASETS_FILE}"
  stageConfig.testCasesPath = "${ML_BENCHMARK_ROOT}/jenkins/${TEST_CASES_FILE}"
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

  def benchmarkEnv = [
          "PATH_PREFIX=${ML_BENCHMARK_ROOT}",
          "DATASETS_PATH=${stageConfig.datasetsPath}",
          "TEST_CASES_PATH=${stageConfig.testCasesPath}",
          "OUTPUT_PATH=${outputPath}",
          "GIT_SHA=${env.GIT_SHA}",
          "GIT_DATE=${env.GIT_DATE.replaceAll(' ', '-')}",
          "BENCHMARK_ALGORITHM=${stageConfig.customData.algorithm}",
          "BUILD_ID=${env.BUILD_ID}",
          "H2O_JAR_PATH=${H2O_ROOT}/build/h2o.jar"
  ]

  try {
    withEnv(benchmarkEnv) {
      defaultStage(pipelineContext, stageConfig)
    }
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
