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