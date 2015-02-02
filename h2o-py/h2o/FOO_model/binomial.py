"""
Binomial Models should be comparable.
"""

from model_base import *
from h2o import H2OConnection
from h2o import H2OFrame
from ..metrics import H2OBinomialModelMetrics


class H2OBinomialModel(ModelBase):

    def __init__(self, raw_model_output=None, algo=None):
        if raw_model_output is None:
            raise H2OModelInstantiationException(
                "Failed to instantiate a Binomial model: no model output found!")
        super(H2OBinomialModel, self).__init__()

        # IN
        self.model_type = self.BINOMIAL
        self.algo = algo
        self.raw_model_output = raw_model_output
        self._key = None           # set by the model_builder

    def summary(self):
        """
        This method prints out various relevant pieces of information for a binomial
        model (e.g. AUC, thresholds for various criteria, etc.)
        :return:
        """
        pass

    def model_performance(self, test_data=None):
        """
        Compute the binary classifier model metrics on `test_data`
        :param test_data: An H2OFrame
        :return: A H2OBinomialMetrics object; prints model metrics summary
        """

        if not test_data:
            raise ValueError("Missing`test_data`.")

        if not isinstance(test_data, H2OFrame):
            raise ValueError("`test_data` must be of type H2OFrame. Got: "
                             + type(test_data))

        fr_key = H2OFrame.send_frame(test_data)

        url_suffix = "ModelMetrics/models/" + self._key + "/frames/" + fr_key
        res = H2OConnection.post_json(url_suffix=url_suffix)
        raw_metrics = res["model_metrics"][0]
        return H2OBinomialModelMetrics(raw_metrics)

    def predict(self, test_data=None):
        pass
