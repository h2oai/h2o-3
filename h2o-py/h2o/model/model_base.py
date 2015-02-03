"""
This module implements the base model class.  All model things inherit from this class.
"""

from frame import H2OFrame
from two_dim_table import H2OTwoDimTable
from connection import H2OConnection
import tabulate

class ModelBase(object):
  def __init__(self, dest_key, model_json):
    self._key = dest_key
    self._model_json = model_json

  def predict(self, test_data):
    """
    Predict on a dataset.
    :param test_data: Data to be predicted on.
    :return: An object of class H2OFrame.
    """
    raise NotImplementedError

  def model_performance(self, test_data):
    """
    Generate model metrics for this model on test_data.
    :param test_data: Data set for which model metrics shall be computed against.
    :return: An object of class H2OModelMetrics.
    """
    if not test_data:  raise ValueError("Missing`test_data`.")
    if not isinstance(test_data, H2OFrame):
      raise ValueError("`test_data` must be of type H2OFrame.  Got: " + type(test_data))
    fr_key = H2OFrame.send_frame(test_data)
    res = H2OConnection.post_json("ModelMetrics/models/" + self._key + "/frames/" + fr_key)
    raw_metrics = res["model_metrics"][0]
    return H2OBinomialModelMetrics(raw_metrics)

  def summary(self):
    """
    Print a detailed summary of the model.
    :return:
    """
    raise NotImplementedError

  def show(self):
    """
    Print innards of model, without regards to type
    :return: None
    """
    model = self._model_json["output"]
    sub = [k for k in model.keys() if k in model["help"].keys() and not k.startswith("_") and k != "help"]
    val = [[model[k]] for k in sub if not isinstance(model[k], H2OTwoDimTable)]
    lab = [model["help"][k] + ":" for k in sub if k != "help"]

    two_dim_tables = [model[k] for k in sub if isinstance(model[k], H2OTwoDimTable)]

    for i in range(len(val)):
      val[i].insert(0, lab[i])

    print
    print "Model Details:"
    print
    print tabulate.tabulate(val, headers=["Description", "Value"])
    print
    for v in two_dim_tables:
      v.show()
