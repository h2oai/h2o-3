from .estimator_base import *


class H2OGradientBoostingEstimator(H2OEstimator):
  """
  Builds gradient boosted classification trees, and gradient boosted regression trees on
  a parsed data set. The default distribution function will guess the model type based on
  the response column type run properly the response column must be an numeric for
  "gaussian" or an enum for "bernoulli" or "multinomial".

  Parameters
  ----------
  model_id : str, optional
    The unique id assigned to the resulting model. If none is given, an id will
    automatically be generated.
  distribution : str
     The distribution function of the response. Must be "AUTO", "bernoulli",
     "multinomial", "poisson", "gamma", "tweedie", "laplace", "quantile" or "gaussian"
  quantile_alpha : float
    Quantile (only for Quantile regression, must be between 0 and 1)
  tweedie_power : float
    Tweedie power (only for Tweedie distribution, must be between 1 and 2)
  ntrees : int
    A non-negative integer that determines the number of trees to grow.
  max_depth : int
    Maximum depth to grow the tree.
  min_rows : int
    Minimum number of rows to assign to terminal nodes.
  learn_rate : float
    Learning rate (from 0.0 to 1.0)
  learn_rate_annealing : float
    Multiply the learning rate by this factor after every tree
  sample_rate : float
    Row sample rate per tree (from 0.0 to 1.0)
  sample_rate_per_class : list
    Row sample rate per tree per class (one per class, from 0.0 to 1.0)
  col_sample_rate : float
    Column sample rate per split (from 0.0 to 1.0)
  col_sample_rate_change_per_level : float
    Relative change of the column sampling rate for every level (from 0.0 to 2.0)
  col_sample_rate_per_tree : float
    Column sample rate per tree (from 0.0 to 1.0)
  nbins : int
    For numerical columns (real/int), build a histogram of (at least) this many bins, then
    split at the best point.
  nbins_top_level : int
    For numerical columns (real/int), build a histogram of (at most) this many bins at the
    root level, then decrease by factor of two per level.
  nbins_cats : int
    For categorical columns (factors), build a histogram of this many bins, then split at
    the best point. Higher values can lead to more overfitting.
  balance_classes : bool
    logical, indicates whether or not to balance training data class counts via
    over/under-sampling (for imbalanced data)
  class_sampling_factors : list
    Desired over/under-sampling ratios per class (in lexicographic
    order). If not specified, sampling factors will be automatically computed to obtain class
    balance during training. Requires balance_classes.
  max_after_balance_size : float
    Maximum relative size of the training data after balancing class counts
    (can be less than 1.0). Ignored if balance_classes is False, which is the
    default behavior.
  seed : int
    Seed for random numbers (affects sampling when balance_classes=T)
  build_tree_one_node : bool
    Run on one node only; no network overhead but fewer cpus used.
    Suitable for small datasets.
  nfolds : int, optional
    Number of folds for cross-validation. If nfolds >= 2, then validation must
    remain empty.
  fold_assignment : str
    Cross-validation fold assignment scheme, if fold_column is not specified.
    Must be "AUTO", "Random" or "Modulo"
  keep_cross_validation_predictions : bool
    Whether to keep the predictions of the cross-validation models
  keep_cross_validation_fold_assignment : bool
    Whether to keep the cross-validation fold assignment.
  score_each_iteration : bool
    Attempts to score each tree.
  score_tree_interval : int
    Score the model after every so many trees. Disabled if set to 0.
  stopping_rounds : int
    Early stopping based on convergence of stopping_metric.
    Stop if simple moving average of length k of the stopping_metric does not improve
    (by stopping_tolerance) for k=stopping_rounds scoring events.
    Can only trigger after at least 2k scoring events. Use 0 to disable.
  stopping_metric : str
    Metric to use for convergence checking, only for _stopping_rounds > 0
    Can be one of "AUTO", "deviance", "logloss", "MSE", "AUC", "r2", "misclassification".
  stopping_tolerance : float
    Relative tolerance for metric-based stopping criterion (stop if relative improvement is not at least this much)
  min_split_improvement : float
    Minimum relative improvement in squared error reduction for a split to happen
  random_split_points : boolean
    Whether to use random split points for histograms (to pick the best split from).
  max_abs_leafnode_pred : float
    Maximum absolute value of a leaf node prediction.

  Returns
  -------
    A new H2OGradientBoostedEstimator object.
  """
  def __init__(self, model_id=None, distribution=None, quantile_alpha=None, tweedie_power=None, ntrees=None,
               max_depth=None, min_rows=None, learn_rate=None, nbins=None,
               sample_rate=None, sample_rate_per_class=None, col_sample_rate=None,
               col_sample_rate_change_per_level=None, col_sample_rate_per_tree=None,
               nbins_top_level=None, nbins_cats=None, balance_classes=None, class_sampling_factors=None,
               max_after_balance_size=None, seed=None, build_tree_one_node=None,
               nfolds=None, fold_assignment=None, keep_cross_validation_predictions=None,
               keep_cross_validation_fold_assignment=None,
               stopping_rounds=None, stopping_metric=None, stopping_tolerance=None,
               score_each_iteration=None, score_tree_interval=None, checkpoint=None,
               min_split_improvement=None, random_split_points=None, max_abs_leafnode_pred=None):
    super(H2OGradientBoostingEstimator, self).__init__()
    self._parms = locals()
    self._parms = {k:v for k,v in self._parms.items() if k!="self"}

  @property
  def distribution(self):
    return self._parms["distribution"]

  @distribution.setter
  def distribution(self, value):
    self._parms["distribution"] = value

  @property
  def quantile_alpha(self):
      return self._parms["quantile_alpha"]

  @quantile_alpha.setter
  def quantile_alpha(self, value):
      self._parms["quantile_alpha"] = value

  @property
  def tweedie_power(self):
    return self._parms["tweedie_power"]

  @tweedie_power.setter
  def tweedie_power(self, value):
    self._parms["tweedie_power"] = value

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
  def learn_rate(self):
    return self._parms["learn_rate"]

  @learn_rate.setter
  def learn_rate(self, value):
    self._parms["learn_rate"] = value

  @property
  def learn_rate_annealing(self):
    return self._parms["learn_rate_annealing"]

  @learn_rate_annealing.setter
  def learn_rate_annealing(self, value):
    self._parms["learn_rate_annealing"] = value

  @property
  def sample_rate(self):
    return self._parms["sample_rate"]

  @sample_rate.setter
  def sample_rate(self, value):
    self._parms["sample_rate"] = value

  @property
  def sample_rate_per_class(self):
    return self._parms["sample_rate_per_class"]

  @sample_rate_per_class.setter
  def sample_rate_per_class(self, value):
    self._parms["sample_rate_per_class"] = value

  @property
  def col_sample_rate(self):
    return self._parms["col_sample_rate"]

  @col_sample_rate.setter
  def col_sample_rate(self, value):
    self._parms["col_sample_rate"] = value

  @property
  def col_sample_rate_change_per_tree(self):
    return self._parms["col_sample_rate_change_per_tree"]

  @col_sample_rate_change_per_tree.setter
  def col_sample_rate_change_per_tree(self, value):
    self._parms["col_sample_rate_change_per_tree"] = value

  @property
  def col_sample_rate_per_tree(self):
    return self._parms["col_sample_rate_per_tree"]

  @col_sample_rate_per_tree.setter
  def col_sample_rate_per_tree(self, value):
    self._parms["col_sample_rate_per_tree"] = value

  @property
  def nbins(self):
    return self._parms["nbins"]

  @nbins.setter
  def nbins(self, value):
    self._parms["nbins"] = value

  @property
  def nbins_top_level(self):
    return self._parms["nbins_top_level"]

  @nbins_top_level.setter
  def nbins_top_level(self, value):
    self._parms["nbins_top_level"] = value

  @property
  def nbins_cats(self):
    return self._parms["nbins_cats"]

  @nbins_cats.setter
  def nbins_cats(self, value):
    self._parms["nbins_cats"] = value

  @property
  def balance_classes(self):
    return self._parms["balance_classes"]

  @balance_classes.setter
  def balance_classes(self, value):
    self._parms["balance_classes"] = value

  @property
  def class_sampling_factors(self):
    return self._parms["class_sampling_factors"]

  @class_sampling_factors.setter
  def class_sampling_factors(self, value):
    self._parms["class_sampling_factors"] = value

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
  def build_tree_one_node(self):
    return self._parms["build_tree_one_node"]

  @build_tree_one_node.setter
  def build_tree_one_node(self, value):
    self._parms["build_tree_one_node"] = value

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
  def keep_cross_validation_fold_assignment(self):
    return self._parms["keep_cross_validation_fold_assignment"]

  @keep_cross_validation_fold_assignment.setter
  def keep_cross_validation_fold_assignment(self, value):
    self._parms["keep_cross_validation_fold_assignment"] = value

  @property
  def score_each_iteration(self):
    return self._parms["score_each_iteration"]

  @score_each_iteration.setter
  def score_each_iteration(self, value):
    self._parms["score_each_iteration"] = value

  @property
  def score_tree_interval(self):
      return self._parms["score_tree_interval"]

  @score_tree_interval.setter
  def score_tree_interval(self, value):
      self._parms["score_tree_interval"] = value

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

  @property
  def min_split_improvement(self):
      return self._parms["min_split_improvement"]

  @min_split_improvement.setter
  def min_split_improvement(self, value):
      self._parms["min_split_improvement"] = value

  @property
  def random_split_points(self):
    return self._parms["random_split_points"]

  @random_split_points.setter
  def random_split_points(self, value):
    self._parms["random_split_points"] = value

  @property
  def max_abs_leafnode_pred(self):
    return self._parms["max_abs_leafnode_pred"]

  @max_abs_leafnode_pred.setter
  def max_abs_leafnode_pred(self, value):
    self._parms["max_abs_leafnode_pred"] = value
