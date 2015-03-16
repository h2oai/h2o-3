"""
AutoEncoder Models should be comparable.
"""

from model_base import *

class H2OAutoEncoderModel(ModelBase):
  """
  Class for Binomial models.  
  """
  def __init__(self, dest_key, model_json):
    super(H2OAutoEncoderModel, self).__init__(dest_key, model_json,H2OAutoEncoderModelMetrics)

  def anomaly
  """
  Return the reconstruction error for an AutoEncoder models
  """
  raise NotImplementedError

class H2OAutoEncoderModelMetrics(object):
  def __init__(self, metric_json):
    self._metric_json = metric_json