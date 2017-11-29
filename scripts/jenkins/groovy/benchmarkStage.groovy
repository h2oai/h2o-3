def call(buildConfig, stageConfig) {

  def defaultStage = load('h2o-3/scripts/jenkins/groovy/defaultStage.groovy')
  def stageNameToDirName = load('h2o-3/scripts/jenkins/groovy/stageNameToDirName.groovy')
  def insideDocker = load('h2o-3/scripts/jenkins/groovy/insideDocker.groovy')

  def DATASETS_FILE = 'accuracy_datasets_docker.csv'
  def TEST_CASES_FILE = "test_cases_${stageConfig.model}.csv"
  def ML_BENCHMARK_ROOT = "${env.WORKSPACE}/${stageNameToDirName(stageConfig.stageName)}/h2o-3/ml-benchmark"

  if (stageConfig.datasetsPath == null) {
    stageConfig.datasetsPath = "${ML_BENCHMARK_ROOT}/h2oR/${DATASETS_FILE}"
  }
  if (stageConfig.testCasesPath == null) {
    stageConfig.testCasesPath = "${ML_BENCHMARK_ROOT}/h2oR/${TEST_CASES_FILE}"
  }
  if (stageConfig.makefilePath == null) {
    stageConfig.makefilePath = "${ML_BENCHMARK_ROOT}/jenkins/Makefile.jenkins"
  }

  if (stageConfig.archiveAdditionalFiles == null) {
    stageConfig.archiveAdditionalFiles = []
  }

  dir (ML_BENCHMARK_ROOT) {
    checkout changelog: false, poll: false, scm: [$class: 'GitSCM', branches: [[name: 'master']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'd57016f6-d172-43ea-bea1-1d6c7c1747a0', url: 'https://github.com/h2oai/ml-benchmark']]]
    sh "sed 's/s3:\\/\\/h2o-benchmark/\\/datasets/g' h2oR/accuracy_datasets_h2o.csv > h2oR/accuracy_datasets_docker.csv"
  }

  def prepareBenchmarkDirStruct = load("${ML_BENCHMARK_ROOT}/jenkins/groovy/prepareBenchmarkDirStruct.groovy")
  def benchmarkFolderConfig = prepareBenchmarkDirStruct(stageConfig.model, env.GIT_SHA, env.BRANCH_NAME)
  GString outputPath = "${env.workspace}/${stageNameToDirName(stageConfig.stageName)}/${benchmarkFolderConfig.getOutputDir()}"
  sh "rm -rf ${outputPath} && mkdir -p ${outputPath}"

  def benchmarkEnv = [
          "DATASETS_PATH=${stageConfig.datasetsPath}",
          "TEST_CASES_PATH=${stageConfig.testCasesPath}",
          "OUTPUT_PATH=${outputPath}",
          "GIT_SHA=${env.GIT_SHA}",
          "GIT_DATE=${env.GIT_DATE.replaceAll(' ', '-')}",
          "BENCHMARK_MODEL=${stageConfig.model}",
          "BUILD_ID=${env.BUILD_ID}",
  ]

  try {
    withEnv(benchmarkEnv) {
      defaultStage(buildConfig, stageConfig)
    }
  } finally {
    insideDocker(benchmarkEnv, stageConfig.image, buildConfig.DOCKER_REGISTRY, 5, 'MINUTES') {
      def persistBenchmarkResults = load("${ML_BENCHMARK_ROOT}/jenkins/groovy/persistBenchmarkResults.groovy")
      persistBenchmarkResults(benchmarkFolderConfig, "${stageNameToDirName(stageConfig.stageName)}")
    }
  }
}

return this
