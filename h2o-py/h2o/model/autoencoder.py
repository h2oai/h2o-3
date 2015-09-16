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

  def anomaly(self,test_data,per_feature=False):
    """
    Obtain the reconstruction error for the input test_data.

    :param test_data: The dataset upon which the reconstruction error is computed.
    :param per_feature: Whether to return the square reconstruction error per feature. Otherwise, return the mean square error.
    :return: Return the reconstruction error.
    """
    if not test_data: raise ValueError("Must specify test data")
    j = H2OConnection.post_json("Predictions/models/" + self._id + "/frames/" + test_data._id, reconstruction_error=True, reconstruction_error_per_feature=per_feature)
    return h2o.get_frame(j["model_metrics"][0]["predictions"]["frame_id"]["name"])