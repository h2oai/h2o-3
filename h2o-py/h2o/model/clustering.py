"""
Clustering Models should be comparable.
"""

from model_base import ModelBase


class H2OClusteringModel(ModelBase):

  def __init__(self, dest_key, model_json):
    super(H2OClusteringModel, self).__init__(dest_key, model_json,H2OClusteringModelMetrics)

  def summary(self):
    """
    This method prints out various relevant pieces of information for a clustering
    model.
    """
    output = self._model_json["output"]
    #centers = output["centers"]
    print "Model Summary:"
    print
    print
    print "Cluster Sizes: " + str(output["size"])
    print
    print "Within-Cluster MSE: " + str(output["within_mse"])
    print
    print "Average Between-Cluster SSE: " + str(output["avg_between_ss"])
    print "Average Overall SSE: " + str(output["avg_ss"])
    print

class H2OClusteringModelMetrics(object):
  def __init__(self, metric_json):
    self._metric_json = metric_json
