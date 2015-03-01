"""
Binomial Models should be comparable.
"""

from model_base import ModelBase
from auc_data import ThresholdCriterion
from auc_data import AUCData
from h2o.model.confusion_matrix import ConfusionMatrix

class H2OBinomialModel(ModelBase):
  """
  Class for Binomial models.  
  """
  def __init__(self, dest_key, model_json):
    super(H2OBinomialModel, self).__init__(dest_key, model_json,H2OBinomialModelMetrics)


class H2OBinomialModelMetrics(object):
  """
  This class is essentially an API for the AUCData object.
  This class contains methods for inspecting the AUC for different criteria.
  To input the different criteria, use the static variable `criteria`
  """
  theCriteria = ThresholdCriterion()
  def __init__(self, metric_json):
    self._metric_json = metric_json
    self._auc_data = AUCData(metric_json)  # AUC Information

  def show(self):
    print
    print "Overall AUC (independent of criterion): " + str(self._auc_data.AUC)
    print "Overall Gini (independent of criterion): " + str(self._auc_data.Gini)
    print
    # print self._auc_data. thresholdsAndMetricScores
    print self._auc_data.maxCriteriaAndMetricScores
    # print self._auc_data.confusion_matrices

  def auc(self):
    return self._auc_data.AUC

  def giniCoef(self):
    return self._auc_data.Gini

  def mse(self):
    return self._metric_json['mse']

  def F1(self, thresholds=None):
    return self.metric(metric="F1", thresholds=thresholds)

  def F2(self, thresholds=None):
    return self.metric(metric="F2", thresholds=thresholds)

  def F0point5(self, thresholds=None):
    return self.metric(metric="F0point5", thresholds=thresholds)

  def accuracy(self, thresholds=None):
    return self.metric(metric="accuracy", thresholds=thresholds)

  def error(self, thresholds=None):
    return self.metric(metric="error", thresholds=thresholds)

  def precision(self, thresholds=None):
    return self.metric(metric="precision", thresholds=thresholds)

  def recall(self, thresholds=None):
    return self.metric(metric="recall", thresholds=thresholds)

  def specificity(self, thresholds=None):
    return self.metric(metric="specificity", thresholds=thresholds)

  def mcc(self, thresholds=None):
    return self.metric(metric="mcc", thresholds=thresholds)

  def max_per_class_error(self, thresholds=None):
    return self.metric(metric="max_per_class_error", thresholds=thresholds)

  def metric(self, metric='accuracy', thresholds=None):
    available_metrics = self._metric_json['thresholdsAndMetricScores'].col_header[1:]
    if(metric not in available_metrics):
      raise ValueError("metric parameter must be one of: " + ", ".join(available_metrics))

    metric_col = self._metric_json['thresholdsAndMetricScores'].col_header.index(metric)
    thresh_and_metrics = []
    if(thresholds is not None):
      if not isinstance(thresholds,list):
        raise ValueError("thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99])")

      for e in self._metric_json['thresholdsAndMetricScores'].cell_values:
          if float(e[0]) in thresholds:
            thresh_and_metrics.append([float(e[0]),e[metric_col]])
    else:
      for e in self._metric_json['thresholdsAndMetricScores'].cell_values:
        thresh_and_metrics.append([float(e[0]),e[metric_col]])

    return thresh_and_metrics

  def confusion_matrices(self, thresholds=None):
    cms = ConfusionMatrix.read_cms(self._metric_json['confusion_matrices'])
    available_thresholds = [float(e[0]) for e in self._metric_json['thresholdsAndMetricScores'].cell_values]
    threshs_and_cms = zip(available_thresholds,cms)

    result =[]
    if(thresholds is not None):
      if not isinstance(thresholds,list):
        raise ValueError("thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99])")

      for tcm in threshs_and_cms:
        if tcm[0] in thresholds:
          result.append([tcm[0],tcm[1]])
    else:
      for tcm in threshs_and_cms:
        result.append([tcm[0],tcm[1]])

    return result
