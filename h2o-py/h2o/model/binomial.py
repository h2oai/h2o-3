"""
Binomial Models
"""

from metrics_base import *


class H2OBinomialModel(ModelBase):
  """
  Class for Binomial models.
  """
  def __init__(self, dest_key, model_json):
    """
    Create a new binomial model.
    """
    super(H2OBinomialModel, self).__init__(dest_key, model_json,H2OBinomialModelMetrics)

  def F1(self, thresholds=None, train=False, valid=False):
    """
    Get the F1 for a set of thresholds.
    If both train and valid are False, return the train.
    If both train and valid are True, return the valid.

    :param train: Return the F1 for training data.
    :param valid: Return the F1 for the validation data.
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The F1 for the given set of thresholds.
    """
    tm = ModelBase._get_metrics(self, *ModelBase._train_or_valid(train, valid))
    if tm is None: return None
    return tm.metric("f1", thresholds=thresholds)

  def F2(self, thresholds=None, train=False, valid=False):
    """
    Get the F2 for a set of thresholds.
    If both train and valid are False, return the train.
    If both train and valid are True, return the valid.

    :param train: Return the F2 for training data.
    :param valid: Return the F2 for the validation data.
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The F2 for the given set of thresholds.
    """
    tm = ModelBase._get_metrics(self, *ModelBase._train_or_valid(train, valid))
    if tm is None: return None
    return tm.metric("f2", thresholds=thresholds)

  def F0point5(self, thresholds=None, train=False, valid=False):
    """
    Get the F0.5 for a set of thresholds.
    If both train and valid are False, return the train.
    If both train and valid are True, return the valid.

    :param train: Return the F0.5 for training data.
    :param valid: Return the F0.5 for the validation data.
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The F0.5 for the given set of thresholds.
    """
    tm = ModelBase._get_metrics(self, *ModelBase._train_or_valid(train, valid))
    if tm is None: return None
    return tm.metric("f0point5", thresholds=thresholds)

  def accuracy(self, thresholds=None, train=False, valid=False):
    """
    Get the accuracy for a set of thresholds.
    If both train and valid are False, return the train.
    If both train and valid are True, return the valid.

    :param train: Return the accuracy for training data.
    :param valid: Return the accuracy for the validation data.
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The accuracy for the given set of thresholds.
    """
    tm = ModelBase._get_metrics(self, *ModelBase._train_or_valid(train, valid))
    if tm is None: return None
    return tm.metric("accuracy", thresholds=thresholds)

  def error(self, thresholds=None, train=False, valid=False):
    """
    Get the error for a set of thresholds.
    If both train and valid are False, return the train.
    If both train and valid are True, return the valid.

    :param train: Return the error for training data.
    :param valid: Return the error for the validation data.
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The error for the given set of thresholds.
    """
    tm = ModelBase._get_metrics(self, *ModelBase._train_or_valid(train, valid))
    if tm is None: return None
    return [[acc[0],1-acc[1]] for acc in tm.metric("accuracy", thresholds=thresholds)]

  def precision(self, thresholds=None, train=False, valid=False):
    """
    Get the precision for a set of thresholds.
    If both train and valid are False, return the train.
    If both train and valid are True, return the valid.

    :param train: Return the precision for training data.
    :param valid: Return the precision for the validation data.
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The precision for the given set of thresholds.
    """
    tm = ModelBase._get_metrics(self, *ModelBase._train_or_valid(train, valid))
    if tm is None: return None
    return tm.metric("precision", thresholds=thresholds)

  def tpr(self, thresholds=None, train=False, valid=False):
    """
    Get the True Positive Rate for a set of thresholds.
    If both train and valid are False, return the train.
    If both train and valid are True, return the valid.

    :param train: Return the True Positive Rate for training data.
    :param valid: Return the True Positive Rate for the validation data.
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The True Positive Rate for the given set of thresholds.
    """
    tm = ModelBase._get_metrics(self, *ModelBase._train_or_valid(train, valid))
    if tm is None: return None
    return tm.metric("tpr", thresholds=thresholds)

  def tnr(self, thresholds=None, train=False, valid=False):
    """
    Get the True Negative Rate for a set of thresholds.
    If both train and valid are False, return the train.
    If both train and valid are True, return the valid.

    :param train: Return the True Negative Rate for training data.
    :param valid: Return the True Negative Rate for the validation data.
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The True Negative Rate for the given set of thresholds.
    """
    tm = ModelBase._get_metrics(self, *ModelBase._train_or_valid(train, valid))
    if tm is None: return None
    return tm.metric("tnr", thresholds=thresholds)

  def fnr(self, thresholds=None, train=False, valid=False):
    """
    Get the False Negative Rates for a set of thresholds.
    If both train and valid are False, return the train.
    If both train and valid are True, return the valid.

    :param train: Return the False Negative Rate for training data.
    :param valid: Return the False Negative Rate for the validation data.
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The False Negative Rate for the given set of thresholds.
    """
    tm = ModelBase._get_metrics(self, *ModelBase._train_or_valid(train, valid))
    if tm is None: return None
    return tm.metric("fnr", thresholds=thresholds)

  def fpr(self, thresholds=None, train=False, valid=False):
    """
    Get the False Positive Rates for a set of thresholds.
    If both train and valid are False, return the train.
    If both train and valid are True, return the valid.

    :param train: Return the False Positive Rate for training data.
    :param valid: Return the False Positive Rate for the validation data.
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The False Positive Rate for the given set of thresholds.
    """
    tm = ModelBase._get_metrics(self, *ModelBase._train_or_valid(train, valid))
    return tm.metric("fpr", thresholds=thresholds)

  def recall(self, thresholds=None, train=False, valid=False):
    """
    Get the Recall (AKA True Positive Rate) for a set of thresholds.
    If both train and valid are False, return the train.
    If both train and valid are True, return the valid.

    :param train: Return the Recall for training data.
    :param valid: Return the Recall for the validation data.
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The Recall for the given set of thresholds.
    """
    tm = ModelBase._get_metrics(self, *ModelBase._train_or_valid(train, valid))
    if tm is None: return None
    return tm.metric("tpr", thresholds=thresholds)

  def sensitivity(self, thresholds=None, train=False, valid=False):
    """
    Get the sensitivity (AKA True Positive Rate or Recall) for a set of thresholds.
    If both train and valid are False, return the train.
    If both train and valid are True, return the valid.

    :param train: Return the Sensitivity for training data.
    :param valid: Return the Sensitivity for the validation data.
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The Sensitivity for the given set of thresholds.
    """
    tm = ModelBase._get_metrics(self, *ModelBase._train_or_valid(train, valid))
    if tm is None: return None
    return tm.metric("tpr", thresholds=thresholds)

  def fallout(self, thresholds=None, train=False, valid=False):
    """
    Get the Fallout (AKA False Positive Rate) for a set of thresholds.
    If both train and valid are False, return the train.
    If both train and valid are True, return the valid.

    :param train: Return the Fallout for training data.
    :param valid: Return the Fallout for the validation data.
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The Fallout for the given set of thresholds.
    """
    tm = ModelBase._get_metrics(self, *ModelBase._train_or_valid(train, valid))
    if tm is None: return None
    return tm.metric("fpr", thresholds=thresholds)

  def missrate(self, thresholds=None, train=False, valid=False):
    """
    Get the miss rate (AKA False Negative Rate) for a set of thresholds.
    If both train and valid are False, return the train.
    If both train and valid are True, return the valid.

    :param train: Return the miss rate for training data.
    :param valid: Return the miss rate for the validation data.
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The miss rate for the given set of thresholds.
    """
    tm = ModelBase._get_metrics(self, *ModelBase._train_or_valid(train, valid))
    if tm is None: return None
    return tm.metric("fnr", thresholds=thresholds)

  def specificity(self, thresholds=None, train=False, valid=False):
    """
    Get the specificity (AKA True Negative Rate) for a set of thresholds.
    If both train and valid are False, return the train.
    If both train and valid are True, return the valid.

    :param train: Return the specificity for training data.
    :param valid: Return the specificity for the validation data.
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The specificity for the given set of thresholds.
    """
    tm = ModelBase._get_metrics(self, *ModelBase._train_or_valid(train, valid))
    if tm is None: return None
    return tm.metric("tnr", thresholds=thresholds)

  def mcc(self, thresholds=None, train=False, valid=False):
    """
    Get the mcc for a set of thresholds.
    If both train and valid are False, return the train.
    If both train and valid are True, return the valid.

    :param train: Return the mcc for training data.
    :param valid: Return the mcc for the validation data.
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The mcc for the given set of thresholds.
    """
    tm = ModelBase._get_metrics(self, *ModelBase._train_or_valid(train, valid))
    if tm is None: return None
    return tm.metric("absolute_MCC", thresholds=thresholds)

  def max_per_class_error(self, thresholds=None, train=False, valid=False):
    """
    Get the max per class error for a set of thresholds.
    If both train and valid are False, return the train.
    If both train and valid are True, return the valid.

    :param train: Return the max per class error for training data.
    :param valid: Return the max per class error for the validation data.
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The max per class error for the given set of thresholds.
    """
    tm = ModelBase._get_metrics(self, *ModelBase._train_or_valid(train, valid))
    if tm is None: return None
    return 1-tm.metric("min_per_class_accuracy", thresholds=thresholds)

  def metric(self, metric, thresholds=None, train=False, valid=False):
    """
    Get the metric value for a set of thresholds.
    If both train and valid are False, return the train.
    If both train and valid are True, return the valid.

    :param metric: A string in {"min_per_class_accuracy", "absolute_MCC", "tnr", "fnr", "fpr", "tpr", "precision", "error", "accuracy", "f0point5", "f2", "f1"}
    :param train: Return the max per class error for training data.
    :param valid: Return the max per class error for the validation data.
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The metric value.
    """
    tm = ModelBase._get_metrics(self, *ModelBase._train_or_valid(train, valid))
    if tm is None: return None
    if not thresholds: thresholds=[tm.find_threshold_by_max_metric(metric)]
    if not isinstance(thresholds,list):
      raise ValueError("thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99])")
    thresh2d = stmelf._metric_json['thresholds_and_metric_scores']
    midx = thresh2d.col_header.index(metric)
    metrics = []
    for t in thresholds:
      idx = tm.find_idx_by_threshold(t)
      row = thresh2d.cell_values[idx]
      metrics.append([t,row[midx]])
    return metrics

  def plot(self, type="roc", train=False, valid=False, **kwargs):
    """
    Produce the desired metric plot
    :param type: the type of metric plot (currently, only ROC supported)
    :param train: Return the max per class error for training data.
    :param valid: Return the max per class error for the validation data.
    :param show: if False, the plot is not shown. matplotlib show method is blocking.
    :return: None
    """
    tm = ModelBase._get_metrics(self, *ModelBase._train_or_valid(train, valid))
    if tm is None: return None
    tm.plot(type=type, **kwargs)

  def confusion_matrix(self, metrics=None, thresholds=None, train=False, valid=False):
    """
    Get the confusion matrix for the specified metrics/thresholds
    If both train and valid are False, return the train.
    If both train and valid are True, return the valid.

    :param metrics: A string (or list of strings) in {"min_per_class_accuracy", "absolute_MCC", "tnr", "fnr", "fpr", "tpr", "precision", "accuracy", "f0point5", "f2", "f1"}
    :param thresholds: A value (or list of values) between 0 and 1
    :param train: Return the max per class error for training data.
    :param valid: Return the max per class error for the validation data.
    :return: a list of ConfusionMatrix objects (if there are more than one to return), or a single ConfusionMatrix (if there is only one)
    """
    tm = ModelBase._get_metrics(self, *ModelBase._train_or_valid(train, valid))
    if tm is None: return None
    return tm.confusion_matrix(metrics=metrics, thresholds=thresholds)

  def find_threshold_by_max_metric(self,metric,train=False,valid=False):
    """
    If both train and valid are False, return the train.
    If both train and valid are True, return the valid.

    :param train: Return the max per class error for training data.
    :param valid: Return the max per class error for the validation data.
    :param metric: A string in {"min_per_class_accuracy", "absolute_MCC", "tnr", "fnr", "fpr", "tpr", "precision", "error", "accuracy", "f0point5", "f2", "f1"}
    :return: the threshold at which the given metric is maximum.
    """
    tm = ModelBase._get_metrics(self, *ModelBase._train_or_valid(train, valid))
    if tm is None: return None
    crit2d = tm._metric_json['max_criteria_and_metric_scores']
    for e in crit2d.cell_values:
      if e[0]=="max "+metric:
        return e[1]
    raise ValueError("No metric "+str(metric))

  def find_idx_by_threshold(self,threshold,train=False,valid=False):
    """
    Retrieve the index in this metric's threshold list at which the given threshold is located.
    If both train and valid are False, return the train.
    If both train and valid are True, return the valid.

    :param train: Return the max per class error for training data.
    :param valid: Return the max per class error for the validation data.
    :param threshold: Find the index of this input threshold.
    :return: Return the index or throw a ValueError if no such index can be found.
    """
    tm = ModelBase._get_metrics(self, *ModelBase._train_or_valid(train, valid))
    if tm is None: return None
    if not isinstance(threshold,float):
      raise ValueError("Expected a float but got a "+type(threshold))
    thresh2d = tm._metric_json['thresholds_and_metric_scores']
    for i,e in enumerate(thresh2d.cell_values):
      t = float(e[0])
      if abs(t-threshold) < 0.00000001 * max(t,threshold):
        return i
    if threshold >= 0 and threshold <= 1:
      thresholds = [float(e[0]) for i,e in enumerate(thresh2d.cell_values)]
      threshold_diffs = [abs(t - threshold) for t in thresholds]
      closest_idx = threshold_diffs.index(min(threshold_diffs))
      closest_threshold = thresholds[closest_idx]
      print "Could not find exact threshold {0}; using closest threshold found {1}." \
        .format(threshold, closest_threshold)
      return closest_idx
    raise ValueError("Threshold must be between 0 and 1, but got {0} ".format(threshold))
