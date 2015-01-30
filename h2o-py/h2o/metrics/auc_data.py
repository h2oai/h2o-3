"""
An object containing information about a binomial classifier.
"""

from confusion_matrix import ConfusionMatrix as CM


class AUCData(object):

    def __init__(self, raw_auc=None):

        if raw_auc is None:
            raise ValueError("Missing data for `raw_auc`.")

        self.actual_domain = raw_auc["actual_domain"]
        self.AUC = raw_auc["AUC"]
        self.Gini = raw_auc["Gini"]

        self.confusion_matrices = CM.read_cms(raw_auc["confusion_matrices"])
        self.F0point5 = raw_auc["F0point5"]
        self.F1 = raw_auc["F1"]
        self.F2 = raw_auc["F2"]
        self.accuracy = raw_auc["accuracy"]
        self.error = raw_auc["errorr"]
        self.max_per_class_error = raw_auc["max_per_class_error"]
        self.mcc = raw_auc["mcc"]
        self.precision = raw_auc["precision"]
        self.recall = raw_auc["recall"]
        self.specificity = raw_auc["specificity"]
        self.thresholds = raw_auc["thresholds"]

        # the "for arrays" -- these are redundant
        self.confusion_matrices_for_crit = \
            CM.read_cms(raw_auc["confusion_matrix_for_criteria"])
        self.F0point5_for_crit = raw_auc["F0point5_for_criteria"]
        self.F1_for_crit = raw_auc["F1_for_criteria"]
        self.F2_for_crit = raw_auc["F2_for_criteria"]
        self.accuracy_for_crit = raw_auc["accuracy_for_criteria"]
        self.error_for_crit = raw_auc["error_for_criteria"]
        self.max_per_class_error_for_crit = raw_auc["max_per_class_error_for_criteria"]
        self.mcc_for_crit = raw_auc["mcc_for_criteria"]
        self.precision_for_crit = raw_auc["precision_for_criteria"]
        self.recall_for_crit = raw_auc["recall_for_criteria"]
        self.specificity_for_crit = raw_auc["specificity_for_criteria"]
        self.thresholds_for_crit = raw_auc["threshold_for_criteria"]


        c = ThresholdCriterion()

        self.criteria = {
            c.MAXF1: {"threshold": self.thresholds_for_crit[0],
                      "value": self.F1_for_crit[0],
                      "cm": self.confusion_matrices_for_crit[0]},
            c.MAXF2: {"threshold": self.thresholds_for_crit[1],
                      "value": self.F2_for_crit[1],
                      "cm": self.confusion_matrices_for_crit[1]},
            c.F0POINT5: {"threshold": self.thresholds_for_crit[2],
                         "value": self.F0point5_for_crit[2],
                         "cm": self.confusion_matrices_for_crit[2]},
            c.ACCURACY: {"threshold": self.thresholds_for_crit[3],
                         "value": self.accuracy_for_crit[3],
                         "cm": self.confusion_matrices_for_crit[3]},
            c.PRECISION: {"threshold": self.thresholds_for_crit[4],
                          "value": self.precision_for_crit[4],
                          "cm": self.confusion_matrices_for_crit[4]},
            c.RECALL: {"threshold": self.thresholds_for_crit[5],
                       "value": self.recall_for_crit[5],
                       "cm": self.confusion_matrices_for_crit[5]},
            c.SPECIFICITY: {"threshold": self.thresholds_for_crit[6],
                            "value": self.specificity_for_crit[6],
                            "cm": self.confusion_matrices_for_crit[6]},
            c.MCC: {"threshold": self.thresholds_for_crit[7],
                    "value": self.mcc_for_crit[7],
                    "cm": self.confusion_matrices_for_crit[7]},
            c.MINMAXPERCLASSERR: {"threshold": self.thresholds_for_crit[8],
                                  "value": self.max_per_class_error_for_crit[8],
                                  "cm": self.confusion_matrices_for_crit[8]}
        }


class ThresholdCriterion(object):
    """
    An Enum for the Threshold Criteria
    """
    MAXF1 = "maximum_F1"
    MAXF2 = "maximum_F2"
    F0POINT5 = "maximum_F0point5"
    ACCURACY = "maximum_Accuracy"
    PRECISION = "maximum_Precision"
    RECALL = "maximum_Recall"
    SPECIFICITY = "maximum_Specificity"
    MCC = "maximum_absolute_MCC"
    MINMAXPERCLASSERR = "minimizing_max_per_class_Error"

    def __init__(self):
        self._criteria = [self.MAXF1, self.MAXF2, self.F0POINT5, self.ACCURACY,
                          self.PRECISION, self.RECALL, self.SPECIFICITY, self.MCC,
                          self.MINMAXPERCLASSERR]

    def crits(self):
        return self._criteria