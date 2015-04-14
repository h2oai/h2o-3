"""
Binomial Models should be comparable.
"""

from model_base import ModelBase
from h2o.model.confusion_matrix import ConfusionMatrix

class H2OBinomialModel(ModelBase):
  """
  Class for Binomial models.  
  """
  def __init__(self, dest_key, model_json):
    super(H2OBinomialModel, self).__init__(dest_key, model_json,H2OBinomialModelMetrics)


class H2OBinomialModelMetrics(object):
  """
  This class is essentially an API for the AUC object.
  This class contains methods for inspecting the AUC for different criteria.
  To input the different criteria, use the static variable `criteria`
  """
  def __init__(self, metric_json):
    if metric_json is None:
      raise ValueError("Missing data for `raw_auc`.")
    self._metric_json = metric_json

  def show(self):
    print
    print "Overall AUC (independent of criterion): " + str(self.auc())
    print "Overall Gini (independent of criterion): " + str(self.giniCoef())
    print
    print self._metric_json["max_criteria_and_metric_scores"]

  def auc(self):
    return self._metric_json['AUC']

  def giniCoef(self):
    return self._metric_json['Gini']

  def mse(self):
    return self._metric_json['mse']

  def F1(self, thresholds=None):
    return self.metric("f1", thresholds=thresholds)

  def F2(self, thresholds=None):
    return self.metric("f2", thresholds=thresholds)

  def F0point5(self, thresholds=None):
    return self.metric("f0point5", thresholds=thresholds)

  def accuracy(self, thresholds=None):
    return self.metric("accuracy", thresholds=thresholds)

  def error(self, thresholds=None):
    return self.metric("error", thresholds=thresholds)

  def precision(self, thresholds=None):
    return self.metric("precision", thresholds=thresholds)

  def recall(self, thresholds=None):
    return self.metric("recall", thresholds=thresholds)

  def specificity(self, thresholds=None):
    return self.metric("specificity", thresholds=thresholds)

  def mcc(self, thresholds=None):
    return self.metric("absolute_MCC", thresholds=thresholds)

  def max_per_class_error(self, thresholds=None):
    return 1-self.metric("min_per_class_correct", thresholds=thresholds)

  def metric(self, metric, thresholds=None):
    if not thresholds: thresholds=[self.find_threshold_by_max_metric(metric)]
    if not isinstance(thresholds,list):
      raise ValueError("thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99])")
    thresh2d = self._metric_json['thresholds_and_metric_scores']
    midx = thresh2d.col_header.index(metric)
    metrics = []
    for t in thresholds:
      idx = self.find_idx_by_threshold(t)
      row = thresh2d.cell_values[idx]
      metrics.append([t,row[midx]])
    return metrics

  def confusion_matrices(self, thresholds=None):
    if not thresholds: thresholds=[self.find_threshold_by_max_metric("f1")]
    if not isinstance(thresholds,list):
      raise ValueError("thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99])")
    thresh2d = self._metric_json['thresholds_and_metric_scores']
    tidx = thresh2d.col_header.index('tps')
    fidx = thresh2d.col_header.index('fps')
    p = self._metric_json['max_criteria_and_metric_scores'].cell_values[tidx-1][2]
    n = self._metric_json['max_criteria_and_metric_scores'].cell_values[fidx-1][2]
    cms = []
    for t in thresholds:
      idx = self.find_idx_by_threshold(t)
      row = thresh2d.cell_values[idx]
      tps = row[tidx]
      fps = row[fidx]
      cms.append([[n-fps,fps],[p-tps,tps]])
    return cms

  def find_threshold_by_max_metric(self,metric):
    crit2d = self._metric_json['max_criteria_and_metric_scores']
    for e in crit2d.cell_values:
      if e[0]==metric:
        return e[1]
    raise ValueError("No metric "+str(metric))

  def find_idx_by_threshold(self,threshold):
    if not isinstance(threshold,float):
      raise ValueError("Expected a float but got a "+type(threshold))
    thresh2d = self._metric_json['thresholds_and_metric_scores']
    for i,e in enumerate(thresh2d.cell_values):
      t = float(e[0])
      if abs(t-threshold) < 0.00000001 * max(t,threshold):
        return i
    raise ValueError("No threshold "+str(threshold))
