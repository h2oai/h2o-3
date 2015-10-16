from .estimator_base import *


class H2ORandomForestEstimator(H2OEstimator):
  def __init__(self, model_id=None, mtries=None, sample_rate=None, build_tree_one_node=None,
               ntrees=None, max_depth=None, min_rows=None, nbins=None, nbins_cats=None,
               binomial_double_trees=None, balance_classes=None, max_after_balance_size=None,
               seed=None, nfolds=None, fold_assignment=None,
               keep_cross_validation_predictions=None, checkpoint=None):
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
    """
    super(H2ORandomForestEstimator, self).__init__()
    self._parms = locals()
    self._parms = {k:v for k,v in self._parms.iteritems() if k!="self"}