"""
AutoEncoder Models
"""

from model_base import *
from metrics_base import *


class H2OAutoEncoderModel(ModelBase):
  """
  Class for AutoEncoder models.
  """
  def __init__(self, dest_key, model_json):
    super(H2OAutoEncoderModel, self).__init__(dest_key, model_json,H2OAutoEncoderModelMetrics)

  def anomaly(self,test_data):
    """
    Obtain the reconstruction error for the input test_data.

    :param test_data: The dataset upon which the reconstruction error is computed.
    :return: Return the reconstruction error.
    """
    if not test_data: raise ValueError("Must specify test data")
    # cbind the test_data vecs together and produce a temp key
    test_data_key = H2OFrame.send_frame(test_data)
    # get the anomaly
    j = H2OConnection.post_json("Predictions/models/" + self._id + "/frames/" + test_data_key, reconstruction_error=True)
    # extract the frame data
    return h2o.get_frame(j["model_metrics"][0]["predictions"]["frame_id"]["name"])