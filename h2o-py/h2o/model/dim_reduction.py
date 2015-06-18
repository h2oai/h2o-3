"""
DimReduction Models
"""

from metrics_base import *

class H2ODimReductionModel(ModelBase):

    def __init__(self, dest_key, model_json):
        super(H2ODimReductionModel, self).__init__(dest_key, model_json,H2ODimReductionModelMetrics)