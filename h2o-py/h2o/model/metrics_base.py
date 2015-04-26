

class MetricsBase(object):
  """
  A parent class to house common metrics available for the various Metrics types.

  The methods here are available acorss different model categories, and so appear here.
  """
  def __init__(self, metric_json,on_train,on_valid,algo):
    self._metric_json = metric_json
    self._on_train = on_train   # train and valid are not mutually exclusive -- could have a test. train and valid only make sense at model build time.
    self._on_valid = on_valid
    self._algo = algo

  def r2(self):
    """
    :return: Retrieve the R^2 coefficient for this set of metrics
    """
    return self._metric_json["r2"]

  def logloss(self):
    """
    :return: Retrieve the log loss for this set of metrics.
    """
    return self._metric_json["logloss"]

  def auc(self):
    """
    :return: Retrieve the AUC for this set of metrics.
    """
    return self._metric_json['AUC']

  def giniCoef(self):
    """
    :return: Retrieve the Gini coefficeint for this set of metrics.
    """
    return self._metric_json['Gini']

  def mse(self):
    """
    :return: Retrieve the MSE for this set of metrics
    """
    return self._metric_json['MSE']

  def residual_deviance(self):
    """
    :return: the residual deviance if the model has residual deviance, or None if no residual deviance.
    """
    if ModelBase._has(self._metric_json, "residual_deviance"):
      return self._metric_json["residual_deviance"]
    return None

  def null_deviance(self):
    """
    :return: the null deviance if the model has residual deviance, or None if no null deviance.
    """
    if ModelBase._has(self._metric_json, "null_deviance"):
      return self._metric_json["null_deviance"]
    return None
