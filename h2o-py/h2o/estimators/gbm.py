from .estimator_base import *


class H2OGradientBoostingEstimator(H2OEstimator,H2OBinomialModel,H2OMultinomialModel,H2ORegressionModel):
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
     "multinomial", "poisson", "gamma", "tweedie" or "gaussian"
  tweedie_power : float
    Tweedie power (only for Tweedie distribution, must be between 1 and 2)
  ntrees : int
    A non-negative integer that determines the number of trees to grow.
  max_depth : int
    Maximum depth to grow the tree.
  min_rows : int
    Minimum number of rows to assign to terminal nodes.
  learn_rate : float
    A value from 0.0 to 1.0
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
  score_each_iteration : bool
    Attempts to score each tree.

  Returns
  -------
    A new H2OGradientBoostedEstimator object.
  """
  def __init__(self, model_id=None, distribution=None, tweedie_power=None, ntrees=None,
               max_depth=None, min_rows=None, learn_rate=None, nbins=None,
               nbins_top_level=None, nbins_cats=None, balance_classes=None,
               max_after_balance_size=None, seed=None, build_tree_one_node=None,
               nfolds=None, fold_assignment=None, keep_cross_validation_predictions=None,
               score_each_iteration=None, checkpoint=None):
    super(H2OGradientBoostingEstimator, self).__init__()
    self.parms = locals()
    self.parms = {k:v for k,v in self.parms.iteritems() if k!="self"}
    self.parms["algo"] = "gbm"