"""
This module implements the base model class.  All model things inherit from this class.
"""

import h2o
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
    # collect the vec_keys
    vec_keys = pred_frame_meta["vec_keys"]
    # get the number of rows
    rows = pred_frame_meta["rows"]
    # get the column names
    cols = [col["label"] for col in pred_frame_meta["columns"]]
    # create a set of H2OVec objects
    vecs = H2OVec.new_vecs(zip(cols, vec_keys), rows)
    # toast the cbound frame
    h2o.remove(test_data_key)
    # return a new H2OFrame object
    return H2OFrame(vecs=vecs)

  def confusionMatrix(self, test_data):
    """
    Returns a confusion matrix based of H2O's default prediction threshold for a dataset
    """
    # cbind the test_data vecs together and produce a temp key
    test_data_key = H2OFrame.send_frame(test_data)
    # get the predictions
    # this job call is blocking
    j = H2OConnection.post_json("Predictions/models/" + self._key + "/frames/" + test_data_key)
    # retrieve the confusion matrix
    cm = j["model_metrics"][0]["cm"]["table"]
    return cm

  def deepfeatures(self, test_data, layer):
    """
    Return hidden layer details
    :param test_data: Data to create a feature space on
    :param layer: 0 index hidden layer
    """
    if not test_data: raise ValueError("Must specify test data")
    # create test_data by cbinding vecs
    test_data_key = H2OFrame.send_frame(test_data)
    # get the deepfeatures of the dataset
    j = H2OConnection.post_json("Predictions/models/" + self._key + "/frames/" + test_data_key, deep_features_hidden_layer=layer)
    # retreive the frame data
    deepfeatures_frame_key = j["destination_key"]["name"]
    df_frame_meta = h2o.frame(deepfeatures_frame_key)["frames"][0]
    # create vecs by extracting vec_keys, col length, and col names
    vec_keys = df_frame_meta["vec_keys"]
    rows = df_frame_meta["rows"]
    cols = [col["label"] for col in df_frame_meta["columns"]]
    vecs = H2OVec.new_vecs(zip(cols, vec_keys), rows)
    # remove test data from kv
    h2o.remove(test_data_key)
    # finally return frame
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
    h2o.remove(fr_key)

    # FIXME need to do the client-side filtering...  PUBDEV-874:   https://0xdata.atlassian.net/browse/PUBDEV-874
    raw_metrics = None
    for mm in res["model_metrics"]:
      if mm["frame"]["name"] == fr_key:
        raw_metrics = mm
        break
    return self._metrics_class(raw_metrics)

  def summary(self):
    """
    Print a detailed summary of the model.
    :return:
    """
    model = self._model_json["output"]
    if model["model_summary"]:
      print
      model["model_summary"].show()  # H2OTwoDimTable object


  def show(self):
    """
    Print innards of model, without regards to type
    :return: None
    """
    model = self._model_json["output"]
    print "Model Details"
    print "============="

    print self.__class__.__name__, ": ", self._model_json["algo_full_name"]
    print "Model Key: ", self._key

    self.summary()

    print
    if self.__class__.__name__ == "H2OMultinomialModel":
      # training metrics
      tm = model["training_metrics"]
      if tm: ModelBase._show_multi_metrics(tm)
      vm = model["validation_metrics"]
      if vm: ModelBase._show_multi_metrics(vm)

    print
    if "scoring_history" in model.keys() and model["scoring_history"]: model["scoring_history"].show()
    if "variable_importances" in model.keys() and model["variable_importances"]: model["variable_importances"].show()


  @staticmethod
  def _show_multi_metrics(metrics, train_or_valid="Training"):
    tm = metrics
    print train_or_valid, " Metrics: "
    print "==================="
    print
    if tm["description"]:     print tm["description"]
    if tm["frame"]:           print "Extract ", train_or_valid.lower(), " frame with `h2o.getFrame(\""+tm["frame"]["name"]+"\")`"
    if tm["MSE"]:             print "MSE on ", train_or_valid, ": ", tm["MSE"]
    if tm["logloss"]:         print "logloss on ", train_or_valid, ": ", tm["logloss"]
    if tm["cm"]:              print "Confusion Matrix on ", train_or_valid, ": ", tm["cm"]["table"].show(header=False)  # H2OTwoDimTable object
    if tm["hit_ratio_table"]: print "Hit Ratio Table on ", train_or_valid, ": ", tm["hit_ratio_table"].show(header=False)



  # Delete from cluster as model goes out of scope
  def __del__(self):
    h2o.remove(self._key)
