"""
Clustering Models should be comparable.
"""

from model_base import *


class H2OClusteringModel(ModelBase):

    def __init__(self, raw_model_output=None, algo=None):
        if raw_model_output is None:
            raise H2OModelInstantiationException(
                "Failed to instantiate a Clustering model: no model output found!")
        super(H2OClusteringModel, self).__init__()
        self.model_type = self.CLUSTERING
        self.algo = algo
        self.raw_model_output = raw_model_output
        self.average_ss = self.raw_model_output["avg_ss"]
        self.average_between_ss = self.raw_model_output["avg_between_ss"]
        self.within_mse = self.raw_model_output["within_mse"]
        self.cluster_sizes = self.raw_model_output["size"]
        self.centers = self.raw_model_output["centers"]

    def summary(self):
        """
        This method prints out various relevant pieces of information for a clustering
        model.
        """
        print "Model Summary:"
        print
        print
        print "Cluster Sizes: " + self.cluster_sizes
        print
        print "Within-Cluster MSE: " + self.within_mse
        print
        print "Average Between-Cluster SSE: " + self.average_between_ss
        print "Average Overall SSE: " + self.average_ss
        print

    def performance(self, test_data=None):
        pass

    def predict(self, test_data=None):
        pass