"""
Regression Models should be comparable.
"""

import math
from model_base import ModelBase

class H2ORegressionModel(ModelBase):
  """
  Class for Regression models.  
  """
  def __init__(self, dest_key, model_json):
    super(H2ORegressionModel, self).__init__(dest_key, model_json,H2ORegressionModelMetrics)


class H2ORegressionModelMetrics(object):
  """
  This class provides an API for inspecting the metrics returned by a regression model.

  It is possible to retrieve the R^2 (1 - mse/variance), mse, and sigma.s
  """
  def __init__(self, metric_json):
    self._metric_json = metric_json

  def r2(self):
    """
    Return the R^2 for this regression model.

    The R^2 value is defined to be 1 - mse/var,
    where var is computed as sigma*sigma.
    :return: The R^2 for this regression model.
    """
    mse  =self._metric_json['mse'  ]
    sigma=self._metric_json['sigma']
    var  =sigma*sigma
    return 1-(mse/var)

  def mse(self):
    """
    :return: The MSE for this regression model.
    """
    return self._metric_json["mse"]

  def sigma(self):
    """
    :return:
    """
    return self._metric_json["sigma"]

  def show(self):
    mse = self._metric_json['mse']
    print "Regression model"
    print "MSE=",mse,"RMSE=",math.sqrt(mse),"sigma=",self._metric_json['sigma']
    print
