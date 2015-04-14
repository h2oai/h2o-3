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

    # setup training metrics
    if "training_metrics" in model_json["output"]:
      tm = model_json["output"]["training_metrics"]
      tm = metrics_class(tm,True,False,model_json["algo"])
      model_json["output"]["training_metrics"] = tm

    # setup validation metrics
    if "validation_metrics" in model_json["output"]:
      vm = model_json["output"]["validation_metrics"]
      if vm is None:
        model_json["output"]["validation_metrics"] = None
      else:
        vm = metrics_class(vm,False,True,model_json["algo"])
        model_json["output"]["validation_metrics"] = vm
    else:
      model_json["output"]["validation_metrics"] = None

    self._model_json = model_json
    self._metrics_class = metrics_class

  def __repr__(self):
    self.show()
    return ""

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
    # collect the vec_ids
    vec_ids = pred_frame_meta["vec_ids"]
    # get the number of rows
    rows = pred_frame_meta["rows"]
    # get the column names
    cols = [col["label"] for col in pred_frame_meta["columns"]]
    # create a set of H2OVec objects
    vecs = H2OVec.new_vecs(zip(cols, vec_ids), rows)
    # toast the cbound frame
    h2o.remove(test_data_key)
    # return a new H2OFrame object
    return H2OFrame(vecs=vecs)

  def confusion_matrix(self, test_data):
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
    deepfeatures_frame_key = j["predictions_name"]["name"]
    df_frame_meta = h2o.frame(deepfeatures_frame_key)["frames"][0]
    # create vecs by extracting vec_ids, col length, and col names
    vec_ids = df_frame_meta["vec_ids"]
    rows = df_frame_meta["rows"]
    cols = [col["label"] for col in df_frame_meta["columns"]]
    vecs = H2OVec.new_vecs(zip(cols, vec_ids), rows)
    # remove test data from kv
    h2o.remove(test_data_key)
    # finally return frame
    return H2OFrame(vecs=vecs)

  def model_performance(self, test_data=None, train=False, valid=False):
    """
    Generate model metrics for this model on test_data.

    :param test_data: Data set for which model metrics shall be computed against. Both train and valid arguments are ignored if test_data is not None.
    :param train: Report the training metrics for the model. If the test_data is the training data, the training metrics are returned.
    :param valid: Report the validation metrics for the model. If train and valid are True, then it defaults to True.
    :return: An object of class H2OModelMetrics.
    """
    if test_data is None:
      if not train and not valid:
        train = True  # default to train

      if train:
        return self._model_json["output"]["training_metrics"]

      if valid:
        return self._model_json["output"]["validation_metrics"]

    else:  # cases dealing with test_data not None
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
      return self._metrics_class(raw_metrics,algo=self._model_json["algo"])

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

  def residual_deviance(self,train=False,valid=False):
    """
    Retreive the residual deviance if this model has the attribute, or None otherwise.

    :param train: Get the residual deviance for the training set. If both train and valid are False, then train is selected by default.
    :param valid: Get the residual deviance for the validation set. If both train and valid are True, then train is selected by default.
    :return: Return the residual deviance, or None if it is not present.
    """
    if not train and not valid:
      train = True
    if train and valid:
      train = True

    if train:
      return self._model_json["output"]["training_metrics"].residual_deviance()
    else:
      return self._model_json["output"]["validation_metrics"].residual_deviance()

  def null_deviance(self,train=False,valid=False):
    """
    Retreive the null deviance if this model has the attribute, or None otherwise.

    :param:  train Get the null deviance for the training set. If both train and valid are False, then train is selected by default.
    :param:  valid Get the null deviance for the validation set. If both train and valid are True, then train is selected by default.
    :return: Return the null deviance, or None if it is not present.
    """
    if not train and not valid:
      train = True
    if train and valid:
      train = True

    if train:
      return self._model_json["output"]["training_metrics"].null_deviance()
    else:
      return self._model_json["output"]["validation_metrics"].null_deviance()

  def pprint_coef(self):
    """
    Pretty print the coefficents table (includes normalized coefficients)
    :return: None
    """
    print self._model_json["output"]["coefficients_table"]  # will return None if no coefs!

  def coef(self):
    """
    :return: Return the coefficients for this model.
    """
    tbl = self._model_json["output"]["coefficients_table"].cell_values
    if tbl is None: return None
    tbl = tbl.cell_values
    return {a[0]:a[1] for a in tbl}

  def coef_norm(self):
    """
    :return: Return the normalized coefficients
    """
    tbl = self._model_json["output"]["coefficients_table"]
    if tbl is None: return None
    tbl = tbl.cell_values
    return {a[0]:a[2] for a in tbl}

  def r2(self, train=False, valid=False):
    """
    Return the R^2 for this regression model.

    The R^2 value is defined to be 1 - MSE/var,
    where var is computed as sigma*sigma.

    :param train: If train is True, then return the R^2 value for the training data. If train and valid are both False, then return the training R^2.
    :param valid: If valid is True, then return the R^2 value for the validation data. If train and valid are both True, then return the validation R^2.
    :return: The R^2 for this regression model.
    """
    tm = ModelBase._get_metrics(self, *ModelBase._train_or_valid(train,valid))
    if tm is None: return None
    return tm.r2()

  def mse(self, train=False,valid=False):
    """
    :param train: If train is True, then return the MSE value for the training data. If train and valid are both False, then return the training MSE.
    :param valid: If valid is True, then return the MSE value for the validation data. If train and valid are both True, then return the validation MSE.
    :return: The MSE for this regression model.
    """
    tm = ModelBase._get_metrics(self, *ModelBase._train_or_valid(train,valid))
    if tm is None: return None
    return tm.mse()

  def logloss(self, train=False, valid=False):
    """
    Get the Log Loss.
    If both train and valid are False, return the train.
    If both train and valid are True, return the valid.

    :param train: Return the log loss for training data.
    :param valid: Return the log loss for the validation data.
    :return: Retrieve the log loss coefficient for this set of metrics
    """
    tm = ModelBase._get_metrics(self,*ModelBase._train_or_valid(train, valid))
    if tm is None: return None
    return tm.logloss()

  def auc(self, train=False, valid=False):
    """
    Get the AUC.
    If both train and valid are False, return the train.
    If both train and valid are True, return the valid.

    :param train: Return the AUC for training data.
    :param valid: Return the AUC for the validation data.
    :return: Retrieve the AUC coefficient for this set of metrics
    """
    tm = ModelBase._get_metrics(self,*ModelBase._train_or_valid(train, valid))
    if tm is None: return None
    tm = tm._metric_json
    return tm.auc()

  def giniCoef(self, train=False, valid=False):
    """
    Get the Gini.
    If both train and valid are False, return the train.
    If both train and valid are True, return the valid.

    :param train: Return the Gini for training data.
    :param valid: Return the Gini for the validation data.
    :return: Retrieve the Gini coefficient for this set of metrics
    """
    tm = ModelBase._get_metrics(self, *ModelBase._train_or_valid(train, valid))
    if tm is None: return None
    tm = tm._metric_json
    return tm.giniCoef()

  @staticmethod
  def _show_multi_metrics(metrics, train_or_valid="Training"):
    tm = metrics._metric_json
    print train_or_valid, " Metrics: "
    print "==================="
    print

    if ModelBase._has(tm,"description"):     print tm["description"]
    if ModelBase._has(tm,"frame"):           print "Extract ", train_or_valid.lower(), " frame with `h2o.getFrame(\""+tm["frame"]["name"]+"\")`"
    if ModelBase._has(tm,"MSE"):             print "MSE on ", train_or_valid, ": ", tm["MSE"]
    if ModelBase._has(tm,"logloss"):         print "logloss on ", train_or_valid, ": ", tm["logloss"]
    if ModelBase._has(tm,"cm"):              print "Confusion Matrix on ", train_or_valid, ": ", tm["cm"]["table"].show(header=False)  # H2OTwoDimTable object
    if ModelBase._has(tm,"hit_ratio_table"): print "Hit Ratio Table on ", train_or_valid, ": ", tm["hit_ratio_table"].show(header=False)

  @staticmethod
  def _get_metrics(o, train, valid):
    if train:
      return o._model_json["output"]["training_metrics"]
    if valid:
      return o._model_json["output"]["validation_metrics"]
    raise ValueError("`_get_metrics` demands `train` or `valid` to be True.")

  @staticmethod
  def _train_or_valid(train,valid):
    """
    Internal static method.

    :param train: a boolean for train. Ignored, however.
    :param valid: a boolean for valid
    :return: true if train, false if valid. If both are false, return True for train.
    """
    if valid: return [False, True]
    return [True,False]

  # Delete from cluster as model goes out of scope
  def __del__(self):
    h2o.remove(self._key)

  @staticmethod
  def _has(dictionary, key):
    return key in dictionary and dictionary[key] is not None
