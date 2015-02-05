"""
Binomial Models should be comparable.
"""

from model_base import ModelBase
from auc_data import ThresholdCriterion
from auc_data import AUCData

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
    print self._auc_data. thresholdsAndMetricScores
    print self._auc_data.maxCriteriaAndMetricScores
    print self._auc_data.confusion_matrices
