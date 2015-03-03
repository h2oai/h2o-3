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

  def size(self):
    return self._model_json["output"]["size"]

  def avg_between_ss(self):
    return self._model_json["output"]["avg_between_ss"]

  def avg_ss(self):
    return self._model_json["output"]["avg_ss"]

  def avg_within_ss(self):
    return self._model_json["output"]["avg_within_ss"]

  def within_mse(self):
    return self._model_json["output"]["within_mse"]

  def centers(self):
    return self._model_json['output']['centers'].cell_values

class H2OClusteringModelMetrics(object):
  def __init__(self, metric_json):
    self._metric_json = metric_json


