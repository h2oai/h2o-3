"""
This module implements the base model class.  All model things inherit from this class.
"""

import h2o
import tabulate
from . import H2OFrame
from . import H2OVec
from . import H2OTwoDimTable
from . import H2OConnection

class ModelBase(object):
  def __init__(self, dest_key, model_json, metrics_class):
    self._key = dest_key
    self._model_json = model_json
    self._metrics_class = metrics_class

  def predict(self, test_data):
    """
    Predict on a dataset.
    :param test_data: Data to be predicted on.
    :return: A new H2OFrame filled with predictions.
    """
    if not test_data: raise ValueError("Must specify test data")
    # cbind the test_data vecs together and produce a temp key
    test_data_key = H2OFrame.send_frame(test_data)
    # get the predictions
    # this job call is blocking
    j = H2OConnection.post_json("Predictions/models/" + self._key + "/frames/" + test_data_key)
    # retrieve the prediction frame
    prediction_frame_key = j["model_metrics"][0]["predictions"]["key"]["name"]
    # get the actual frame meta dta
    pred_frame_meta = h2o.frame(prediction_frame_key)["frames"][0]
    # collect the veckeys
    veckeys = pred_frame_meta["veckeys"]
    # get the number of rows
    rows = pred_frame_meta["rows"]
    # get the column names
    cols = [col["label"] for col in pred_frame_meta["columns"]]
    # create a set of H2OVec objects
    vecs = H2OVec.new_vecs(zip(cols, veckeys), rows)
    # toast the cbound frame
    h2o.remove(test_data_key)
    # return a new H2OFrame object
    return H2OFrame(vecs=vecs)

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
    return self._metrics_class(raw_metrics)

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
