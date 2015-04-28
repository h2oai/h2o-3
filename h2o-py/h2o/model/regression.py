"""
Regression Models
"""

import math
from metrics_base import *

class H2ORegressionModel(ModelBase):
  """
  Class for Regression models.  
  """
  def __init__(self, dest_key, model_json):
    super(H2ORegressionModel, self).__init__(dest_key, model_json,H2ORegressionModelMetrics)

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