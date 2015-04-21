"""
Clustering Models should be comparable.
"""

from model_base import ModelBase


class H2OClusteringModel(ModelBase):

  def __init__(self, dest_key, model_json):
    super(H2OClusteringModel, self).__init__(dest_key, model_json,H2OClusteringModelMetrics)

  def size(self):
    tm = H2OClusteringModel._get_metrics(self)
    return [ v[2] for v in  tm["centroid_stats"].cell_values]

  def avg_between_ss(self):
    tm = H2OClusteringModel._get_metrics(self)
    return tm["avg_between_ss"]

  def avg_ss(self):
    tm = H2OClusteringModel._get_metrics(self)
    return tm["avg_ss"]

  def avg_within_ss(self):
    tm = H2OClusteringModel._get_metrics(self)
    return tm["avg_within_ss"]

  def within_mse(self):
    tm = H2OClusteringModel._get_metrics(self)
    return [ v[-1] for v in  tm["centroid_stats"].cell_values]

  def centers(self):
    o = self._model_json["output"]
    cvals = o["centers"].cell_values
    centers = []
    for cidx, cval in enumerate(cvals):
      centers.append(list(cvals[cidx])[1:])
    return centers

  def centers_std(self):
    o = self._model_json["output"]
    cvals = o["centers_std"].cell_values
    centers_std = []
    for cidx, cval in enumerate(cvals):
      centers_std.append(list(cvals[cidx])[1:])
    return centers_std

  def centroid_stats(self):
    tm = H2OClusteringModel._get_metrics(self)
    return tm["centroid_stats"]

  # TODO: move this out to ModelBase
  @staticmethod
  def _get_metrics(o):
    return o._model_json["output"]["training_metrics"]

class H2OClusteringModelMetrics(object):
  def __init__(self, metric_json):
    self._metric_json = metric_json


