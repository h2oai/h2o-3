"""
Binomial Models should be comparable.
"""

from model_base import *


class H2OClusteringModel(ModelBase):

    def __init__(self, raw_model_output=None, algo=None):
        if raw_model_output is None:
            raise H2OModelInstantiationException(
                "Failed to instantiate a Binomial model: no model output found!")
        super(H2OClusteringModel, self).__init__()
        self.model_type = self.CLUSTERING
        self.algo = algo
        self.raw_model_output = raw_model_output

    def summary(self):
        """
        This method prints out various relevant pieces of information for a clustering
        model.
        :return:
        """
        pass

    def performance(self, test_data=None):
        pass

    def predict(self, test_data=None):
        pass