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

  It is possible to retrieve the R^2 (1 - MSE/variance) and MSE
  """
  def __init__(self,metric_json,on_train=False,on_valid=False,algo=""):
    self._metric_json = metric_json
    self._on_train = on_train   # train and valid are not mutually exclusive -- could have a test. train and valid only make sense at model build time.
    self._on_valid = on_valid   # train and valid are not mutually exclusive -- could have a test. train and valid only make sense at model build time.
    self._algo = algo

  def r2(self):
    """
    Return the R^2 for this regression model.

    The R^2 value is defined to be 1 - MSE/var,
    where var is computed as sigma*sigma.
    :return: The R^2 for this regression model.
    """
    return self._metric_json["r2"]

  def mse(self):
    """
    :return: The MSE for this regression model.
    """
    return self._metric_json["MSE"]

  def show(self):
    mse = self._metric_json['MSE']
    print "Regression model"
    print "MSE=",mse,"RMSE=",math.sqrt(mse),"r2=",self.r2()
    print
