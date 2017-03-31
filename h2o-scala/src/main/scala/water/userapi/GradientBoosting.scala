package water.userapi

import hex.ModelMetricsRegression
import hex.tree.gbm.GBM
import hex.tree.gbm.GBMModel
import water.Scope
import water.fvec.Frame

/**
  * UserApi adapter for GBM operations
  *
  * Created by vpatryshev on 2/27/17.
  */
class GradientBoosting {

  val params = new GBMModel.GBMParameters()

  def this(train: Frame, valid: Frame) {
    this()
    params._train = train._key
    params._valid = valid._key
  }

  @volatile lazy val trainingJob: GBM = new GBM(params)

  @volatile lazy val model: GBMModel = Scope.track(trainingJob.trainModel.get)

  def output: GBMModel.GBMOutput = model._output

  def metrics: Option[ModelMetricsRegression] = {
    Option(output._validation_metrics) collect {
      case mmr: ModelMetricsRegression => mmr
    }
  }

  def responseColumn(rc: String): GradientBoosting = {
    params._response_column = rc
    this
  }

  def seed(i: Int): GradientBoosting ={
    params._seed = i
    this
  }

  def minRows(i: Int): GradientBoosting ={
    params._min_rows = i
    this
  }

  def maxDepth(i: Int): GradientBoosting ={
    params._max_depth = i
    this
  }

  def nTrees(i: Int): GradientBoosting ={
    params._ntrees = i
    this
  }

  def colSampleRate(colSampleRate: Double): GradientBoosting = {
    params._col_sample_rate = colSampleRate
    this
  }

  def colSampleRatePerTree(colSampleRatePerTree: Double): GradientBoosting = {
    params._col_sample_rate_per_tree = colSampleRatePerTree
    this
  }

  def sampleRate(sampleRate: Double): GradientBoosting = {
    params._sample_rate = sampleRate
    this
  }

  def learnRate(v: Float): GradientBoosting = {
    params._learn_rate = v
    this
  }

  def nTrees: Int = params._ntrees

  def scoreTreeInterval(i: Int): GradientBoosting = {
    params._score_tree_interval = i
    this
  }

  def minSplitImprovement(v: Double): GradientBoosting = {
    params._min_split_improvement = v
    this
  }

  def logLoss: Double = 
    output._scored_valid(output._scored_valid.length - 1)._logloss
}