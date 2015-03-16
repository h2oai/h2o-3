"""
An object containing information about a binomial classifier.
"""

from confusion_matrix import ConfusionMatrix


class AUCData(object):

  def __init__(self, raw_auc):
    if raw_auc is None:
      raise ValueError("Missing data for `raw_auc`.")
    self.AUC = raw_auc["AUC"]
    self.Gini = raw_auc["Gini"]
    self.confusion_matrices = ConfusionMatrix.read_cms(raw_auc["confusion_matrices"])
    # Two Dim Table
    self. thresholds_and_metric_scores = raw_auc[ "thresholds_and_metric_scores"]
    self.max_criteria_and_metric_scores = raw_auc["max_criteria_and_metric_scores"]


class ThresholdCriterion(object):
  """
  An Enum for the Threshold Criteria
  """
  MAXF1 = "maximum F1"
  MAXF2 = "maximum F2"
  F0POINT5 = "maximum F0point5"
  ACCURACY = "maximum Accuracy"
  PRECISION = "maximum Precision"
  RECALL = "maximum Recall"
  SPECIFICITY = "maximum Specificity"
  MCC = "maximum absolute MCC"
  MINMAXPERCLASSERR = "minimizing max per class Error"

  def __init__(self):
    self._criteria = [self.MAXF1, self.MAXF2, self.F0POINT5, self.ACCURACY,
                      self.PRECISION, self.RECALL, self.SPECIFICITY, self.MCC,
                      self.MINMAXPERCLASSERR]

  def crits(self):
    return self._criteria
