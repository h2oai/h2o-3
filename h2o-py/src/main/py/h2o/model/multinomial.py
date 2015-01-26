"""
Binomial Models should be comparable.
"""

import ModelBase
from . import H2OModelInstantiationException


class H2OMultinomialModel(ModelBase):

    def __init__(self, raw_model_output=None):
        if raw_model_output is None:
            raise H2OModelInstantiationException(
                "Failed to instantiate a Binomial model: no model output found!")
        super(H2OMultinomialModel, self).__init__()
        self.model_type = None
        self.algo = None

    def summary(self):
        """
        This method prints out various relevant pieces of information for a multinomial
        model.
        :return:
        """
        pass

    def performance(self, test_data=None):
        pass

    def predict(self, test_data=None):
        pass