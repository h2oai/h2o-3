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
  This class is essentially an API for the AUCData object.
  This class contains methods for inspecting the AUC for different criteria.
  To input the different criteria, use the static variable `criteria`
  """
  def __init__(self, metric_json):
    self._metric_json = metric_json

  def r2(self):
    mse  =self._metric_json['mse'  ]
    sigma=self._metric_json['sigma']
    var  =sigma*sigma
    return 1-(mse/var)

  def show(self):
    mse = self._metric_json['mse']
    print "Regression model"
    print "MSE=",mse,"RMSE=",math.sqrt(mse),"sigma=",self._metric_json['sigma']
    print
