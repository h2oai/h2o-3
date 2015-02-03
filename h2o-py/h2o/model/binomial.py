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
    self._auc_data = AUCData(metric_json["auc"])  # AUC Information

  def show(self, criterion=None, threshold=None):
    if threshold is not None:  raise NotImplementedError
    if criterion is None:  criterion = self.theCriteria.MAXF1
    # check the criteria passed in
    if criterion not in self.theCriteria.crits():
      raise ValueError("Invalid criterion. Must be one of: " + self.theCriteria.crits() + ". Got: " + criterion)

    auc_data_for_crit = self._auc_data.criteria[criterion]

    print
    print "Overall AUC (independent of criterion): " + str(self._auc_data.AUC)
    print "Overall Gini (independent of criterion): " + str(self._auc_data.Gini)
    print
    print "Threshold for " + criterion + ": " + str(auc_data_for_crit["threshold"])
    print "Value of " + criterion + ": " + str(auc_data_for_crit["value"])
    print "Confusion Matrix for " + criterion + ": "
    auc_data_for_crit["cm"].show()
