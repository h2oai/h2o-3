"""
Multinomial Models
"""

from . import H2OFrame
from . import H2OConnection
from metrics_base import *


class H2OMultinomialModel(ModelBase):
    def __init__(self, dest_key, model_json):
        super(H2OMultinomialModel, self).__init__(dest_key, model_json,H2OMultinomialModelMetrics)

    def confusion_matrix(self, data):
        """
        Returns a confusion matrix based of H2O's default prediction threshold for a dataset
        """
        if not isinstance(data, H2OFrame): raise ValueError("data argument must be of type H2OFrame, but got {0}"
                                                            .format(type(data)))
        j = H2OConnection.post_json("Predictions/models/" + self._id + "/frames/" + data._id)
        return j["model_metrics"][0]["cm"]["table"]

    def hit_ratio_table(self, train=False, valid=False, xval=False):
        """
        Retrieve the Hit Ratios

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
        and "xval"

        :param train: If train is True, then return the R^2 value for the training data.
        :param valid: If valid is True, then return the R^2 value for the validation data.
        :param xval:  If xval is True, then return the R^2 value for the cross validation data.
        :return: The R^2 for this regression model.
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k,v in zip(tm.keys(),tm.values()): m[k] = None if v is None else v.hit_ratio_table()
        return m.values()[0] if len(m) == 1 else m
