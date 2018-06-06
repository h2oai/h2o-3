def call(final pipelineContext, final stageConfig) {

  def defaultStage = load('h2o-3/scripts/jenkins/groovy/defaultStage.groovy')
  defaultStage(pipelineContext, stageConfig)

  jacoco(execPattern: "${stageConfig.stageDir}/h2o-3/build/reports/jacoco/report.exec")
}

return this
