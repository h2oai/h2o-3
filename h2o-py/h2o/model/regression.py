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

  def r2(self, train=False, valid=False):
    """
    Return the R^2 for this regression model.

    The R^2 value is defined to be 1 - MSE/var,
    where var is computed as sigma*sigma.

    :param train: If train is True, then return the R^2 value for the training data. If train and valid are both False, then return the training R^2.
    :param valid: If valid is True, then return the R^2 value for the validation data. If train and valid are both True, then return the validation R^2.
    :return: The R^2 for this regression model.
    """
    tm = ModelBase._get_metrics(self, *ModelBase._train_or_valid(train,valid))
    return tm["r2"]

  def mse(self, train=False,valid=False):
    """
    :param train: If train is True, then return the MSE value for the training data. If train and valid are both False, then return the training MSE.
    :param valid: If valid is True, then return the MSE value for the validation data. If train and valid are both True, then return the validation MSE.
    :return: The MSE for this regression model.
    """
    tm = ModelBase._get_metrics(self, *ModelBase._train_or_valid(train,valid))
    return tm["MSE"]

  def show(self, train=False, valid=False):
    """
    Show the MSE, RMSE, and R2 for this regression model.
    :param train: If train is True, then show for the training data. If train and valid are both False, then show for training.
    :param valid: If valid is True, then show for the validation data. If train and valid are both True, then show for validation.
    :return: None
    """
    mse = self.mse(train,valid)
    print "Regression model"
    print "MSE=",mse,"RMSE=",math.sqrt(mse),"r2=",self.r2()
    print

  def residual_deviance(self, train=False, valid=False):
    """
    Get the residual deviance of the GLM.
    :param train: If train is True, then return the residual deviance on the training set.
    :param valid: If valid is True, then return the residual deviance on the validation set.
    :return: Returns None if the model has no residual deviance.
    """
    tm = ModelBase._get_metrics(self, *ModelBase._train_or_valid(train,valid))
    if ModelBase._has(tm, "residual_deviance"):
      return tm["residual_deviance"]
    return None

  def null_deviance(self, train=False, valid=False):
    """
    Get the null deviance of the GLM.
    :param train: If train is True, then return the null deviance on the training set.
    :param valid: If valid is True, then return the null deviance on the validation set.
    :return: Returns None if the model has no null deviance.
    """
    tm = ModelBase._get_metrics(self, *ModelBase._train_or_valid(train,valid))
    if ModelBase._has(tm, "null_deviance"):
      return tm["null_deviance"]
    return None