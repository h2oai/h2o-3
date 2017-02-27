package water.userapi;

import hex.ModelMetricsRegression;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import water.Scope;

/**
 * UserApi adapter for GBM operations
 * 
 * Created by vpatryshev on 2/27/17.
 */
class GradientBoosting {
  
  /*private*/ final GBMModel.GBMParameters params = new GBMModel.GBMParameters();
  
  GradientBoosting(Dataset.TrainAndValid split) {
    params._train = split.train.frame()._key;
    params._valid = split.valid.frame()._key;
  }

  public volatile GBM trainingJob = null;

  public GBM trainingJob() {
    if (trainingJob == null) trainingJob = new GBM(params);
    return trainingJob;
  }

  volatile private GBMModel model = null;

  // Build a first model; all remaining models should be equal
  private GBMModel model() {
    if (model == null) model = Scope.track(trainingJob().trainModel().get());
    return model;
  }
  
  public ModelMetricsRegression metrics() {
    return (ModelMetricsRegression) model()._output._validation_metrics;
  }

  public GradientBoosting responseColumn(String rc) {
    params._response_column = rc;
    return this;
  }

  public GradientBoosting seed(int i) {
    params._seed = i;
    return this;
  }

  public GradientBoosting minRows(int i) {
    params._min_rows = i;
    return this;
  }

  public GradientBoosting maxDepth(int i) {
    params._max_depth = i;
    return this;
  }

  public GradientBoosting nTrees(int i) {
    params._ntrees = i;
    return this;
  }

  public GradientBoosting colSampleRate(double colSampleRate) {
    params._col_sample_rate = colSampleRate;
    return this;
  }

  public GradientBoosting colSampleRatePerTree(double colSampleRatePerTree) {
    params._col_sample_rate_per_tree = colSampleRatePerTree;
    return this;
  }

  public GradientBoosting sampleRate(double sampleRate) {
    params._sample_rate = sampleRate;
    return this;
  }

  public GradientBoosting learnRate(float v) {
    params._learn_rate = v;
    return this;
  }

  public int nTrees() {
    return params._ntrees;
  }

  public GradientBoosting scoreTreeInterval(int i) {
    params._score_tree_interval = i;
    return this;
  }
}
