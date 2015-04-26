"""
Multinomial Models should be comparable.
"""

from model_base import *
from metrics_base import *


class H2OMultinomialModel(ModelBase):

    def __init__(self, dest_key, model_json):
        super(H2OMultinomialModel, self).__init__(dest_key, model_json,H2OMultinomialModelMetrics)

class H2OMultinomialModelMetrics(MetricsBase):
  def __init__(self, metric_json, on_train=False, on_valid=False, algo=""):
    super(H2OMultinomialModelMetrics, self).__init__(metric_json, on_train, on_valid,algo)