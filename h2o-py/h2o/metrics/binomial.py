"""
Binary Classifier Model Metrics And Performance
"""

from auc_data import AUCData
from auc_data import ThresholdCriterion


class H2OBinomialModelMetrics(object):
    """
    This class is essentially an API for the AUCData object.

    This class contains methods for inspecting the AUC for different criteria.

    To input the different criteria, use the static variable `criteria`
    """

    theCriteria = ThresholdCriterion()

    def __init__(self, json_raw=None):
        if not json_raw:
            raise ValueError(
                "Missing Data: Cannot instantiate a new H2OBinomialModelMetrics object.")

        self.auc_data = AUCData(json_raw["auc"])  # AUC Information

    def show(self, criterion=None, threshold=None):

        if threshold is not None:
            raise ValueError("Unimplemented, threshold specification is not available.")

        if criterion is None:
            criterion = self.theCriteria.MAXF1

        # check the criteria passed in
        if criterion not in self.theCriteria.crits():
            raise ValueError("Invalid criterion. Must be one of: "
                             + self.theCriteria.crits() + ". Got: " + criterion)

        auc_data_for_crit = self.auc_data.criteria[criterion]

        print
        print "Overall AUC (independent of criterion): " + str(self.auc_data.AUC)
        print "Overall Gini (independent of criterion): " + str(self.auc_data.Gini)
        print
        print "Threshold for " + criterion + ": " + str(auc_data_for_crit["threshold"])
        print "Value of " + criterion + ": " + str(auc_data_for_crit["value"])
        print "Confusion Matrix for " + criterion + ": "
        auc_data_for_crit["cm"].show()
