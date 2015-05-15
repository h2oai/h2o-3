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
        test_data_key = H2OFrame.send_frame(data)
        # get the predictions
        # this job call is blocking
        j = H2OConnection.post_json("Predictions/models/" + self._key + "/frames/" + test_data_key)
        # retrieve the confusion matrix
        cm = j["model_metrics"][0]["cm"]["table"]
        return cm