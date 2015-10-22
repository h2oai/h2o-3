from .estimator_base import *


class H2ONaiveBayesEstimator(H2OEstimator):
  def __init__(self,model_id=None, laplace=None, threshold=None, eps=None,
               compute_metrics=None, balance_classes=None,
               max_after_balance_size=None, nfolds=None, fold_assignment=None,
               keep_cross_validation_predictions=None, checkpoint=None):
    """
    The naive Bayes classifier assumes independence between predictor variables
    conditional on the response, and a Gaussian distribution of numeric predictors with
    mean and standard deviation computed from the training dataset. When building a naive
    Bayes classifier, every row in the training dataset that contains at least one NA will
    be skipped completely. If the test dataset has missing values, then those predictors
    are omitted in the probability calculation during prediction.

    Parameters
    ----------
    laplace : int
      A positive number controlling Laplace smoothing. The default zero disables smoothing.
    threshold : float
      The minimum standard deviation to use for observations without enough data.
      Must be at least 1e-10.
    eps : float
      A threshold cutoff to deal with numeric instability, must be positive.
    compute_metrics : bool
      A logical value indicating whether model metrics should be computed. Set to FALSE
      to reduce the runtime of the algorithm.
    nfolds : int, optional
      Number of folds for cross-validation. If nfolds >= 2, then validation must remain
      empty.
    fold_assignment : str
      Cross-validation fold assignment scheme, if fold_column is not specified
      Must be "AUTO", "Random" or "Modulo"
    keep_cross_validation_predictions :  bool
      Whether to keep the predictions of the cross-validation models.

    Returns
    -------
      Returns instance of H2ONaiveBayesEstimator
    """
    super(H2ONaiveBayesEstimator, self).__init__()
    self._parms = locals()
    self._parms = {k:v for k,v in self._parms.iteritems() if k!="self"}

  @property
  def laplace(self):
    return self._parms["laplace"]

  @laplace.setter
  def laplace(self, value):
    self._parms["laplace"] = value

  @property
  def threshold(self):
    return self._parms["threshold"]

  @threshold.setter
  def threshold(self, value):
    self._parms["threshold"] = value

  @property
  def eps(self):
    return self._parms["eps"]

  @eps.setter
  def eps(self, value):
    self._parms["eps"] = value

  @property
  def compute_metrics(self):
    return self._parms["compute_metrics"]

  @compute_metrics.setter
  def compute_metrics(self, value):
    self._parms["compute_metrics"] = value

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
  def checkpoint(self):
    return self._parms["checkpoint"]

  @checkpoint.setter
  def checkpoint(self, value):
    self._parms["checkpoint"] = value

