from .estimator_base import *


class H2ONaiveBayesEstimator(H2OEstimator,H2OBinomialModel,H2OMultinomialModel,H2ORegressionModel):
  def __init__(self, laplace=None, threshold=None, eps=None, compute_metrics=None,
               balance_classes=None,max_after_balance_size=None, nfolds=None,
               fold_assignment=None, keep_cross_validation_predictions=None,
               checkpoint=None):
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
    self.parms = locals()
    self.parms = {k:v for k,v in self.parms.iteritems() if k!="self"}
    self.parms["algo"] = "naivebayes"