"""
Binomial Models should be comparable.
"""

from model_base import ModelBase
#from h2o import H2OConnection
#from h2o import H2OFrame
#from ..metrics import H2OBinomialModelMetrics

class H2OBinomialModel(ModelBase):

  def __init__(self, dest_key, model_json):
    super(H2OBinomialModel, self).__init__(dest_key, model_json)

