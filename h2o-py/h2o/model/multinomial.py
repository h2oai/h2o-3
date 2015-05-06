"""
Multinomial Models
"""

from metrics_base import *
class H2OMultinomialModel(ModelBase):
    def __init__(self, dest_key, model_json):
        super(H2OMultinomialModel, self).__init__(dest_key, model_json,H2OMultinomialModelMetrics)
