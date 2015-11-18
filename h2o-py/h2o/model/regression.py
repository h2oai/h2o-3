from model_base import ModelBase


class H2ORegressionModel(ModelBase):

  def _make_model(self):
    return H2ORegressionModel()

  def plot(self, timestep="AUTO", metric="AUTO", **kwargs):
    """
    Plots training set (and validation set if available) scoring history for an H2ORegressionModel. The timestep and metric
    arguments are restricted to what is available in its scoring history.

    :param timestep: A unit of measurement for the x-axis.
    :param metric: A unit of measurement for the y-axis.
    :return: A scoring history plot.
    """

    if self._model_json["algo"] in ("deeplearning", "drf", "gbm"):
      if metric == "AUTO": metric = "MSE"
      elif metric not in ("MSE","deviance"):
        raise ValueError("metric for H2ORegressionModel must be one of: AUTO, MSE, deviance")

    self._plot(timestep=timestep, metric=metric, **kwargs)

def _mean_var(frame, weights=None):
  """
  Compute the (weighted) mean and variance

  :param frame: Single column H2OFrame
  :param weights: optional weights column
  :return: The (weighted) mean and variance
  """
  return frame.mean()[0], frame.var()


def h2o_mean_absolute_error(y_actual, y_predicted, weights=None):
  """
  Mean absolute error regression loss.

  :param y_actual: H2OFrame of actual response.
  :param y_predicted: H2OFrame of predicted response.
  :param weights: (Optional) sample weights
  :return: loss (float) (best is 0.0)

  """
  ModelBase._check_targets(y_actual, y_predicted)
  return (y_predicted-y_actual).abs().mean()[0]


def h2o_mean_squared_error(y_actual, y_predicted, weights=None):
  """
  Mean squared error regression loss

  :param y_actual: H2OFrame of actual response.
  :param y_predicted: H2OFrame of predicted response.
  :param weights: (Optional) sample weights
  :return: loss (float) (best is 0.0)
  """
  ModelBase._check_targets(y_actual, y_predicted)
  return ((y_predicted-y_actual)**2).mean()[0]


def h2o_median_absolute_error(y_actual, y_predicted):
  """
  Median absolute error regression loss

  :param y_actual: H2OFrame of actual response.
  :param y_predicted: H2OFrame of predicted response.
  :return: loss (float) (best is 0.0)
  """
  ModelBase._check_targets(y_actual, y_predicted)
  return (y_predicted-y_actual).abs().median()


def h2o_explained_variance_score(y_actual, y_predicted, weights=None):
  """
  Explained variance regression score function

  :param y_actual: H2OFrame of actual response.
  :param y_predicted: H2OFrame of predicted response.
  :param weights: (Optional) sample weights
  :return: the explained variance score (float)
  """
  ModelBase._check_targets(y_actual, y_predicted)

  _, numerator   = _mean_var(y_actual - y_predicted, weights)
  _, denominator = _mean_var(y_actual, weights)
  if denominator == 0.0:
    return 1. if numerator == 0 else 0.  # 0/0 => 1, otherwise, 0
  return 1 - numerator / denominator


def h2o_r2_score(y_actual, y_predicted, weights=1.):
  """
  R^2 (coefficient of determination) regression score function

  :param y_actual: H2OFrame of actual response.
  :param y_predicted: H2OFrame of predicted response.
  :param weights: (Optional) sample weights
  :return: R^2 (float) (best is 1.0, lower is worse)
  """
  ModelBase._check_targets(y_actual, y_predicted)
  numerator   = (weights * (y_actual - y_predicted) ** 2).sum()
  denominator = (weights * (y_actual - y_actual.mean()[0]) ** 2).sum()

  if denominator == 0.0:
    return 1. if numerator == 0. else 0.  # 0/0 => 1, else 0
  return 1 - numerator / denominator