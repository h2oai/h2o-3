"""
Regression Models should be comparable.
"""

from model_base import *


class H2ORegressionModel(ModelBase):

    def __init__(self, raw_model_output=None, algo=None):
        if raw_model_output is None:
            raise H2OModelInstantiationException(
                "Failed to instantiate a Regression model: no model output found!")
        super(H2ORegressionModel, self).__init__()
        self.model_type = self.REGRESSION
        self.algo = algo
        self.raw_model_output = raw_model_output

    def summary(self):
        """
        This method prints out various relevant pieces of information for a regression
        model.
        :return:
        """
        pass

    def performance(self, test_data=None):
        pass

    def predict(self, test_data=None):
        pass