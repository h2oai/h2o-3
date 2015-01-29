"""
Multinomial Models should be comparable.
"""

from model_base import *


class H2OMultinomialModel(ModelBase):

    def __init__(self, raw_model_output=None, algo=None):
        if raw_model_output is None:
            raise H2OModelInstantiationException(
                "Failed to instantiate a Multinomial model: no model output found!")
        super(H2OMultinomialModel, self).__init__()
        self.model_type = self.MULTINOMIAL
        self.algo = algo
        self.raw_model_output = raw_model_output

    def summary(self):
        """
        This method prints out various relevant pieces of information for a multinomial
        model.
        :return:
        """
        pass

    def model_performance(self, test_data=None):
        pass

    def predict(self, test_data=None):
        pass