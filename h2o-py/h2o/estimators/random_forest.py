from .estimator_base import *


class H2ORandomForestEstimator(H2OEstimator):
  def __init__(self, model_id=None, mtries=None, sample_rate=None, build_tree_one_node=None,
               ntrees=None, max_depth=None, min_rows=None, nbins=None, nbins_cats=None,
               binomial_double_trees=None, balance_classes=None, max_after_balance_size=None,
               seed=None, nfolds=None, fold_assignment=None,
               stopping_rounds=None, stopping_metric=None, stopping_tolerance=None,
               score_each_iteration=None, keep_cross_validation_predictions=None, checkpoint=None):
    """Builds a Random Forest Model on an H2OFrame

    Parameters
    ----------
    model_id : str, optional
      The unique id assigned to the resulting model. If none is given, an id will
      automatically be generated.
    mtries : int
      Number of variables randomly sampled as candidates at each split. If set to -1,
      defaults to sqrt{p} for classification, and p/3 for regression, where p is the
      number of predictors.
    sample_rate : float
      Sample rate, from 0 to 1.0.
    build_tree_one_node : bool
      Run on one node only; no network overhead but fewer CPUs used.
      Suitable for small datasets.
    ntrees : int
      A non-negative integer that determines the number of trees to grow.
    max_depth : int
      Maximum depth to grow the tree.
    min_rows : int
      Minimum number of rows to assign to terminal nodes.
    nbins : int
      For numerical columns (real/int), build a histogram of (at least) this many bins,
      then split at the best point.
    nbins_top_level : int
      For numerical columns (real/int), build a histogram of (at most) this many bins at
      the root level, then decrease by factor of two per level.
    nbins_cats : int
      For categorical columns (factors), build a histogram of this many bins, then split
      at the best point. Higher values can lead to more overfitting.
    binomial_double_trees : bool
      or binary classification: Build 2x as many trees (one per class) - can lead to
      higher accuracy.
    balance_classes : bool
      logical, indicates whether or not to balance training data class counts via
      over/under-sampling (for imbalanced data)
    max_after_balance_size : float
      Maximum relative size of the training data after balancing class counts
      (can be less than 1.0). Ignored if balance_classes is False,
      which is the default behavior.
    seed : int
      Seed for random numbers (affects sampling) - Note: only reproducible when
      running single threaded
    nfolds : int, optional
      Number of folds for cross-validation. If nfolds >= 2, then validation must
      remain empty.
    fold_assignment : str
      Cross-validation fold assignment scheme, if fold_column is not specified
      Must be "AUTO", "Random" or "Modulo"
    keep_cross_validation_predictions : bool
      Whether to keep the predictions of the cross-validation models
    score_each_iteration : bool
      Attempts to score each tree.
    stopping_rounds : int
      Early stopping based on convergence of stopping_metric.
      Stop if simple moving average of length k of the stopping_metric does not improve
      (by stopping_tolerance) for k=stopping_rounds scoring events.
      Can only trigger after at least 2k scoring events. Use 0 to disable.
    stopping_metric : str
      Metric to use for convergence checking, only for _stopping_rounds > 0
      Can be one of "AUTO", "deviance", "logloss", "MSE", "AUC", "r2", "misclassification".
    stopping_tolerance : float
      Relative tolerance for metric-based stopping criterion (stop if relative improvement
      is not at least this much)
      Relative tolerance for metric-based stopping criterion (stop if relative improvement is not at least this much)
    """
    super(H2ORandomForestEstimator, self).__init__()
    self._parms = locals()
    self._parms = {k:v for k,v in self._parms.iteritems() if k!="self"}

  @property
  def mtries(self):
    return self._parms["mtries"]

  @mtries.setter
  def mtries(self, value):
    self._parms["mtries"] = value

  @property
  def sample_rate(self):
    return self._parms["sample_rate"]

  @sample_rate.setter
  def sample_rate(self, value):
    self._parms["sample_rate"] = value

  @property
  def build_tree_one_node(self):
    return self._parms["build_tree_one_node"]

  @build_tree_one_node.setter
  def build_tree_one_node(self, value):
    self._parms["build_tree_one_node"] = value

  @property
  def ntrees(self):
    return self._parms["ntrees"]

  @ntrees.setter
  def ntrees(self, value):
    self._parms["ntrees"] = value

  @property
  def max_depth(self):
    return self._parms["max_depth"]

  @max_depth.setter
  def max_depth(self, value):
    self._parms["max_depth"] = value

  @property
  def min_rows(self):
    return self._parms["min_rows"]

  @min_rows.setter
  def min_rows(self, value):
    self._parms["min_rows"] = value

  @property
  def nbins(self):
    return self._parms["nbins"]

  @nbins.setter
  def nbins(self, value):
    self._parms["nbins"] = value

  @property
  def nbins_cats(self):
    return self._parms["nbins_cats"]

  @nbins_cats.setter
  def nbins_cats(self, value):
    self._parms["nbins_cats"] = value

  @property
  def binomial_double_trees(self):
    return self._parms["binomial_double_trees"]

  @binomial_double_trees.setter
  def binomial_double_trees(self, value):
    self._parms["binomial_double_trees"] = value

  @property
  def balance_classes(self):
    return self._parms["balance_classes"]

  @balance_classes.setter
  def balance_classes(self, value):
    self._parms["balance_classes"] = value

  @property
  def max_after_balance_size(self):
    return self._parms["max_after_balance_size"]

  @max_after_balance_size.setter
  def max_after_balance_size(self, value):
    self._parms["max_after_balance_size"] = value

  @property
  def seed(self):
    return self._parms["seed"]

  @seed.setter
  def seed(self, value):
    self._parms["seed"] = value

  @property
  def nfolds(self):
    return self._parms["nfolds"]

  @nfolds.setter
  def nfolds(self, value):
    self._parms["nfolds"] = value

  @property
  def fold_assignment(self):
    return self._parms["fold_assignment"]

  @fold_assignment.setter
  def fold_assignment(self, value):
    self._parms["fold_assignment"] = value

  @property
  def keep_cross_validation_predictions(self):
    return self._parms["keep_cross_validation_predictions"]

  @keep_cross_validation_predictions.setter
  def keep_cross_validation_predictions(self, value):
    self._parms["keep_cross_validation_predictions"] = value

  @property
  def score_each_iteration(self):
    return self._parms["score_each_iteration"]

  @score_each_iteration.setter
  def score_each_iteration(self, value):
    self._parms["score_each_iteration"] = value

  @property
  def stopping_rounds(self):
    return self._parms["stopping_rounds"]

  @stopping_rounds.setter
  def stopping_rounds(self, value):
    self._parms["stopping_rounds"] = value

  @property
  def stopping_metric(self):
    return self._parms["stopping_metric"]

  @stopping_metric.setter
  def stopping_metric(self, value):
    self._parms["stopping_metric"] = value

  @property
  def stopping_tolerance(self):
    return self._parms["stopping_tolerance"]

  @stopping_tolerance.setter
  def stopping_tolerance(self, value):
    self._parms["stopping_tolerance"] = value

  @property
  def checkpoint(self):
    return self._parms["checkpoint"]

  @checkpoint.setter
  def checkpoint(self, value):
    self._parms["checkpoint"] = value

