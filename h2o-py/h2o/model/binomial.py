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
  def __init__(self,metric_json,on_train=False,on_valid=False,algo=""):
    """
    Create a new Binomial Metrics object (essentially a wrapper around some json)
    :param metric_json: A blob of json holding all of the needed information
    :param on_train: Metrics built on training data (default is False)
    :param on_valid: Metrics built on validation data (default is False)
    :param algo: The algorithm the metrics are based off of (e.g. deeplearning, gbm, etc.)
    :return: A new H2OBinomialModelMetrics object.
    """
    if metric_json is None:
      raise ValueError("Missing data for `raw_auc`.")
    self._metric_json = metric_json
    self._on_train = on_train   # train and valid are not mutually exclusive -- could have a test. train and valid only make sense at model build time.
    self._on_valid = on_valid   # train and valid are not mutually exclusive -- could have a test. train and valid only make sense at model build time.
    self._algo = algo


  def __repr__(self):
    self.show()
    return ""

  def show(self):
    """
    Display a short summary of the binomial metrics.
    :return: None
    """
    print
    print "H2OBinomialMetrics: " + self._algo
    reported_on = "** Reported on {} data. **"
    if self._on_train:
      print reported_on.format("train")
    elif self._on_valid:
      print reported_on.format("validation")
    else:
      print reported_on.format("test")
    print
    print "MSE: " + str(self.mse())
    print "R^2: "  + str(self.r2())
    print "LogLoss: " + str(self.logloss())
    print "AUC: " + str(self.auc())
    print "Gini: " + str(self.giniCoef())
    print self._metric_json["max_criteria_and_metric_scores"]

  def r2(self):
    """
    :return: Retrieve the R^2 coefficient for this set of metrics
    """
    return self._metric_json["r2"]

  def logloss(self):
    """
    :return: Retrieve the log loss for this set of metrics.
    """
    return self._metric_json["logloss"]

  def auc(self):
    """
    :return: Retrieve the AUC for this set of metrics.
    """
    return self._metric_json['AUC']

  def giniCoef(self):
    """
    :return: Retrieve the Gini coefficeint for this set of metrics.
    """
    return self._metric_json['Gini']

  def mse(self):
    """
    :return: Retrieve the MSE for this set of metrics
    """
    return self._metric_json['MSE']

  def F1(self, thresholds=None):
    """
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The F1 for the given set of thresholds.
    """
    return self.metric("f1", thresholds=thresholds)

  def F2(self, thresholds=None):
    """
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The F2 for this set of metrics and thresholds
    """
    return self.metric("f2", thresholds=thresholds)

  def F0point5(self, thresholds=None):
    """
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The F0point5 for this set of metrics and thresholds.
    """
    return self.metric("f0point5", thresholds=thresholds)

  def accuracy(self, thresholds=None):
    """
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The accuracy for this set of metrics and thresholds
    """
    return self.metric("accuracy", thresholds=thresholds)

  def error(self, thresholds=None):
    """
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The error for this set of metrics and thresholds.
    """
    return self.metric("error", thresholds=thresholds)

  def precision(self, thresholds=None):
    """
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The precision for this set of metrics and thresholds.
    """
    return self.metric("precision", thresholds=thresholds)

  def tpr(self, thresholds=None):
    """
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The True Postive Rate
    """
    return self.metric("tpr", thresholds=thresholds)

  def tnr(self, thresholds=None):
    """
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The True Negative Rate
    """
    return self.metric("tnr", thresholds=thresholds)

  def fnr(self, thresholds=None):
    """
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The False Negative Rate
    """
    return self.metric("fnr", thresholds=thresholds)

  def fpr(self, thresholds=None):
    """
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The False Positive Rate
    """
    return self.metric("fpr", thresholds=thresholds)

  def recall(self, thresholds=None):
    """
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: Recall for this set of metrics and thresholds
    """
    return self.metric("tpr", thresholds=thresholds)

  def sensitivity(self, thresholds=None):
    """
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: Sensitivity or True Positive Rate for this set of metrics and thresholds
    """
    return self.metric("tpr", thresholds=thresholds)

  def fallout(self, thresholds=None):
    """
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The fallout or False Positive Rate for this set of metrics and thresholds
    """
    return self.metric("fpr", thresholds=thresholds)

  def missrate(self, thresholds=None):
    """
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: THe missrate or False Negative Rate.
    """
    return self.metric("fnr", thresholds=thresholds)

  def specificity(self, thresholds=None):
    """
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The specificity or True Negative Rate.
    """
    return self.metric("tnr", thresholds=thresholds)

  def mcc(self, thresholds=None):
    """
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The absolute MCC (a value between 0 and 1, 0 being totally dissimilar, 1 being identical)
    """
    return self.metric("absolute_MCC", thresholds=thresholds)

  def max_per_class_error(self, thresholds=None):
    """
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: Return 1 - min_per_class_accuracy
    """
    return 1-self.metric("min_per_class_accuracy", thresholds=thresholds)

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
