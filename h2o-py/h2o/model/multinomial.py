"""
Multinomial Models should be comparable.
"""

from model_base import *


class H2OMultinomialModel(ModelBase):

    def __init__(self, dest_key, model_json):
        super(H2OMultinomialModel, self).__init__(dest_key, model_json,H2OMultinomialModelMetrics)

class H2OMultinomialModelMetrics(object):
  def __init__(self, metric_json,on_train,on_valid,algo):
    self._metric_json = metric_json
    self._on_train = on_train   # train and valid are not mutually exclusive -- could have a test. train and valid only make sense at model build time.
    self._on_valid = on_valid   # train and valid are not mutually exclusive -- could have a test. train and valid only make sense at model build time.
    self._algo = algo