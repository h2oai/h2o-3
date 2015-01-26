"""
Clustering Models should be comparable.
"""

from model_base import *
from pprint import pprint as pp


class H2OClusteringModel(ModelBase):

    def __init__(self, raw_model_output=None, algo=None):
        if raw_model_output is None:
            raise H2OModelInstantiationException(
                "Failed to instantiate a Clustering model: no model output found!")
        super(H2OClusteringModel, self).__init__()
        self.model_type = self.CLUSTERING
        self.algo = algo
        self.raw_model_output = raw_model_output

    def summary(self):
        """
        This method prints out various relevant pieces of information for a clustering model.
        """
        print "Model Summary:"
        print
        print self.raw_model_output["centers"]
        print
        print "Cluster Sizes: " + str(self.raw_model_output["size"])
        print
        print "Within-Cluster MSE: " + str(self.raw_model_output["within_mse"])
        print
        print "Average Between-Cluster SSE: " + str(self.raw_model_output["avg_between_ss"])
        print "Average Overall SSE: " + str(self.raw_model_output["avg_ss"])
        print

    def performance(self, test_data=None):
        pass

    def predict(self, test_data=None):
        pass