from model_base import ModelBase


class H2OBinomialModel(ModelBase):
  def F1(self, thresholds=None, train=False, valid=False, xval=False):
    """Get the F1 value for a set of thresholds

    If all are False (default), then return the training metric value.
    If more than one options is set to True, then return a dictionary of metrics where
    the keys are "train", "valid", and "xval".

    Parameters
    ----------
      thresholds : list, optional
        If None, then the thresholds in this set of metrics will be used.
      train : bool, optional
        If True, return the F1 value for the training data.
      valid : bool, optional
        If True, return the F1 value for the validation data.
      xval : bool, optional
        If True, return the F1 value for each of the cross-validated splits.

    Returns
    -------
      The F1 values for the specified key(s).

    Examples
    --------
    >>> import h2o as ml
    >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
    >>> ml.init()
    >>> rows=[[1,2,3,4,0],[2,1,2,4,1],[2,1,4,2,1],[0,1,2,34,1],[2,3,4,1,0]]*50
    >>> fr = ml.H2OFrame(rows)
    >>> fr[4] = fr[4].asfactor()
    >>> model = H2OGradientBoostingEstimator(ntrees=10, max_depth=10, nfolds=4)
    >>> model.train(x=range(4), y=4, training_frame=fr)
    >>> model.F1(train=True)
    """
    tm = ModelBase._get_metrics(self, train, valid, xval)
    m = {}
    for k,v in tm.iteritems():
      m[k] = None if v is None else v.metric("f1", thresholds=thresholds)
    return m.values()[0] if len(m) == 1 else m

  def F2(self, thresholds=None, train=False, valid=False, xval=False):
    """Get the F2 for a set of thresholds.
    If all are False (default), then return the training metric value.
    If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
    and "xval"

    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :param train: If train is True, then return the F2 value for the training data.
    :param valid: If valid is True, then return the F2 value for the validation data.
    :param xval:  If xval is True, then return the F2 value for the cross validation data.
    :return: The F2 for this binomial model.
    """
    tm = ModelBase._get_metrics(self, train, valid, xval)
    m = {}
    for k,v in zip(tm.keys(),tm.values()): m[k] = None if v is None else v.metric("f2", thresholds=thresholds)
    return m.values()[0] if len(m) == 1 else m

  def F0point5(self, thresholds=None, train=False, valid=False, xval=False):
    """Get the F0.5 for a set of thresholds.
    If all are False (default), then return the training metric value.
    If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
    and "xval"

    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :param train: If train is True, then return the F0point5 value for the training data.
    :param valid: If valid is True, then return the F0point5 value for the validation data.
    :param xval:  If xval is True, then return the F0point5 value for the cross validation data.
    :return: The F0point5 for this binomial model.
    """
    tm = ModelBase._get_metrics(self, train, valid, xval)
    m = {}
    for k,v in zip(tm.keys(),tm.values()): m[k] = None if v is None else v.metric("f0point5", thresholds=thresholds)
    return m.values()[0] if len(m) == 1 else m

  def accuracy(self, thresholds=None, train=False, valid=False, xval=False):
    """
    Get the accuracy for a set of thresholds.
    If all are False (default), then return the training metric value.
    If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
    and "xval"

    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :param train: If train is True, then return the accuracy value for the training data.
    :param valid: If valid is True, then return the accuracy value for the validation data.
    :param xval:  If xval is True, then return the accuracy value for the cross validation data.
    :return: The accuracy for this binomial model.
    """
    tm = ModelBase._get_metrics(self, train, valid, xval)
    m = {}
    for k,v in zip(tm.keys(),tm.values()): m[k] = None if v is None else v.metric("accuracy", thresholds=thresholds)
    return m.values()[0] if len(m) == 1 else m

  def error(self, thresholds=None, train=False, valid=False, xval=False):
    """
    Get the error for a set of thresholds.
    If all are False (default), then return the training metric value.
    If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
    and "xval"

    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :param train: If train is True, then return the error value for the training data.
    :param valid: If valid is True, then return the error value for the validation data.
    :param xval:  If xval is True, then return the error value for the cross validation data.
    :return: The error for this binomial model.
    """
    tm = ModelBase._get_metrics(self, train, valid, xval)
    m = {}
    for k,v in zip(tm.keys(),tm.values()): m[k] = None if v is None else [[acc[0],1-acc[1]] for acc in v.metric("accuracy", thresholds=thresholds)]
    return m.values()[0] if len(m) == 1 else m

  def precision(self, thresholds=None, train=False, valid=False, xval=False):
    """
    Get the precision for a set of thresholds.
    If all are False (default), then return the training metric value.
    If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
    and "xval"

    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :param train: If train is True, then return the precision value for the training data.
    :param valid: If valid is True, then return the precision value for the validation data.
    :param xval:  If xval is True, then return the precision value for the cross validation data.
    :return: The precision for this binomial model.
    """
    tm = ModelBase._get_metrics(self, train, valid, xval)
    m = {}
    for k,v in zip(tm.keys(),tm.values()): m[k] = None if v is None else v.metric("precision", thresholds=thresholds)
    return m.values()[0] if len(m) == 1 else m

  def tpr(self, thresholds=None, train=False, valid=False, xval=False):
    """
    Get the True Positive Rate for a set of thresholds.
    If all are False (default), then return the training metric value.
    If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
    and "xval"

    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :param train: If train is True, then return the tpr value for the training data.
    :param valid: If valid is True, then return the tpr value for the validation data.
    :param xval:  If xval is True, then return the tpr value for the cross validation data.
    :return: The tpr for this binomial model.
    """
    tm = ModelBase._get_metrics(self, train, valid, xval)
    m = {}
    for k,v in zip(tm.keys(),tm.values()): m[k] = None if v is None else v.metric("tpr", thresholds=thresholds)
    return m.values()[0] if len(m) == 1 else m

  def tnr(self, thresholds=None, train=False, valid=False, xval=False):
    """
    Get the True Negative Rate for a set of thresholds.
    If all are False (default), then return the training metric value.
    If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
    and "xval"

    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :param train: If train is True, then return the tnr value for the training data.
    :param valid: If valid is True, then return the tnr value for the validation data.
    :param xval:  If xval is True, then return the tnr value for the cross validation data.
    :return: The F1 for this binomial model.
    """
    tm = ModelBase._get_metrics(self, train, valid, xval)
    m = {}
    for k,v in zip(tm.keys(),tm.values()): m[k] = None if v is None else v.metric("tnr", thresholds=thresholds)
    return m.values()[0] if len(m) == 1 else m

  def fnr(self, thresholds=None, train=False, valid=False, xval=False):
    """
    Get the False Negative Rates for a set of thresholds.
    If all are False (default), then return the training metric value.
    If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
    and "xval"

    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :param train: If train is True, then return the fnr value for the training data.
    :param valid: If valid is True, then return the fnr value for the validation data.
    :param xval:  If xval is True, then return the fnr value for the cross validation data.
    :return: The fnr for this binomial model.
    """
    tm = ModelBase._get_metrics(self, train, valid, xval)
    m = {}
    for k,v in zip(tm.keys(),tm.values()): m[k] = None if v is None else v.metric("fnr", thresholds=thresholds)
    return m.values()[0] if len(m) == 1 else m

  def fpr(self, thresholds=None, train=False, valid=False, xval=False):
    """
    Get the False Positive Rates for a set of thresholds.
    If all are False (default), then return the training metric value.
    If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
    and "xval"

    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :param train: If train is True, then return the fpr value for the training data.
    :param valid: If valid is True, then return the fpr value for the validation data.
    :param xval:  If xval is True, then return the fpr value for the cross validation data.
    :return: The fpr for this binomial model.
    """
    tm = ModelBase._get_metrics(self, train, valid, xval)
    m = {}
    for k,v in zip(tm.keys(),tm.values()): m[k] = None if v is None else v.metric("fpr", thresholds=thresholds)
    return m.values()[0] if len(m) == 1 else m

  def recall(self, thresholds=None, train=False, valid=False, xval=False):
    """
    Get the Recall (AKA True Positive Rate) for a set of thresholds.
    If all are False (default), then return the training metric value.
    If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
    and "xval"

    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :param train: If train is True, then return the recall value for the training data.
    :param valid: If valid is True, then return the recall value for the validation data.
    :param xval:  If xval is True, then return the recall value for the cross validation data.
    :return: The recall for this binomial model.
    """
    tm = ModelBase._get_metrics(self, train, valid, xval)
    m = {}
    for k,v in zip(tm.keys(),tm.values()): m[k] = None if v is None else v.metric("tpr", thresholds=thresholds)
    return m.values()[0] if len(m) == 1 else m

  def sensitivity(self, thresholds=None, train=False, valid=False, xval=False):
    """
    Get the sensitivity (AKA True Positive Rate or Recall) for a set of thresholds.
    If all are False (default), then return the training metric value.
    If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
    and "xval"


    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :param train: If train is True, then return the sensitivity value for the training data.
    :param valid: If valid is True, then return the sensitivity value for the validation data.
    :param xval:  If xval is True, then return the sensitivity value for the cross validation data.
    :return: The sensitivity for this binomial model.
    """
    tm = ModelBase._get_metrics(self, train, valid, xval)
    m = {}
    for k,v in zip(tm.keys(),tm.values()): m[k] = None if v is None else v.metric("tpr", thresholds=thresholds)
    return m.values()[0] if len(m) == 1 else m

  def fallout(self, thresholds=None, train=False, valid=False, xval=False):
    """
    Get the Fallout (AKA False Positive Rate) for a set of thresholds.
    If all are False (default), then return the training metric value.
    If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
    and "xval"

    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :param train: If train is True, then return the fallout value for the training data.
    :param valid: If valid is True, then return the fallout value for the validation data.
    :param xval:  If xval is True, then return the fallout value for the cross validation data.
    :return: The fallout for this binomial model.
    """
    tm = ModelBase._get_metrics(self, train, valid, xval)
    m = {}
    for k,v in zip(tm.keys(),tm.values()): m[k] = None if v is None else v.metric("fpr", thresholds=thresholds)
    return m.values()[0] if len(m) == 1 else m

  def missrate(self, thresholds=None, train=False, valid=False, xval=False):
    """
    Get the miss rate (AKA False Negative Rate) for a set of thresholds.
    If all are False (default), then return the training metric value.
    If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
    and "xval"

    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :param train: If train is True, then return the missrate value for the training data.
    :param valid: If valid is True, then return the missrate value for the validation data.
    :param xval:  If xval is True, then return the missrate value for the cross validation data.
    :return: The missrate for this binomial model.
    """
    tm = ModelBase._get_metrics(self, train, valid, xval)
    m = {}
    for k,v in zip(tm.keys(),tm.values()): m[k] = None if v is None else v.metric("fnr", thresholds=thresholds)
    return m.values()[0] if len(m) == 1 else m

  def specificity(self, thresholds=None, train=False, valid=False, xval=False):
    """
    Get the specificity (AKA True Negative Rate) for a set of thresholds.
    If all are False (default), then return the training metric value.
    If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
    and "xval"

    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :param train: If train is True, then return the specificity value for the training data.
    :param valid: If valid is True, then return the specificity value for the validation data.
    :param xval:  If xval is True, then return the specificity value for the cross validation data.
    :return: The specificity for this binomial model.
    """
    tm = ModelBase._get_metrics(self, train, valid, xval)
    m = {}
    for k,v in zip(tm.keys(),tm.values()): m[k] = None if v is None else v.metric("tnr", thresholds=thresholds)
    return m.values()[0] if len(m) == 1 else m

  def mcc(self, thresholds=None, train=False, valid=False, xval=False):
    """
    Get the mcc for a set of thresholds.
    If all are False (default), then return the training metric value.
    If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
    and "xval"

    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :param train: If train is True, then return the mcc value for the training data.
    :param valid: If valid is True, then return the mcc value for the validation data.
    :param xval:  If xval is True, then return the mcc value for the cross validation data.
    :return: The mcc for this binomial model.
    """
    tm = ModelBase._get_metrics(self, train, valid, xval)
    m = {}
    for k,v in zip(tm.keys(),tm.values()): m[k] = None if v is None else v.metric("absolute_MCC", thresholds=thresholds)
    return m.values()[0] if len(m) == 1 else m

  def max_per_class_error(self, thresholds=None, train=False, valid=False, xval=False):
    """
    Get the max per class error for a set of thresholds.
    If all are False (default), then return the training metric value.
    If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
    and "xval"

    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :param train: If train is True, then return the max_per_class_error value for the training data.
    :param valid: If valid is True, then return the max_per_class_error value for the validation data.
    :param xval:  If xval is True, then return the max_per_class_error value for the cross validation data.
    :return: The max_per_class_error for this binomial model.
    """
    tm = ModelBase._get_metrics(self, train, valid, xval)
    m = {}
    for k,v in zip(tm.keys(),tm.values()): m[k] = None if v is None else [[mpca[0],1-mpca[1]] for mpca in v.metric("min_per_class_accuracy", thresholds=thresholds)]
    return m.values()[0] if len(m) == 1 else m

  def metric(self, metric, thresholds=None, train=False, valid=False, xval=False):
    """
    Get the metric value for a set of thresholds.
    If all are False (default), then return the training metric value.
    If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
    and "xval"

    :param train: If train is True, then return the metrics for the training data.
    :param valid: If valid is True, then return the metrics for the validation data.
    :param xval:  If xval is True, then return the metrics for the cross validation data.
    :return: The metrics for this binomial model.
    """
    tm = ModelBase._get_metrics(self, train, valid, xval)
    m = {}
    for k,v in zip(tm.keys(),tm.values()): m[k] = None if v is None else v.metric(metric,thresholds)
    return m.values()[0] if len(m) == 1 else m

  def plot(self, timestep="AUTO", metric="AUTO", **kwargs):
    """
    Plots training set (and validation set if available) scoring history for an H2OBinomialModel. The timestep and metric
    arguments are restricted to what is available in its scoring history.

    :param timestep: A unit of measurement for the x-axis.
    :param metric: A unit of measurement for the y-axis.
    :return: A scoring history plot.
    """

    if self._model_json["algo"] in ("deeplearning", "drf", "gbm"):
      if metric == "AUTO": metric = "logloss"
      elif metric not in ("logloss","AUC","classification_error","MSE"):
        raise ValueError("metric for H2OBinomialModel must be one of: AUTO, logloss, AUC, classification_error, MSE")

    self._plot(timestep=timestep, metric=metric, **kwargs)

  def roc(self, train=False, valid=False, xval=False):
    """
    Return the coordinates of the ROC curve for a given set of data,
    as a two-tuple containing the false positive rates as a list and true positive
    rates as a list.
    If all are False (default), then return is the training data.
    If more than one ROC curve is requested, the data is returned as a dictionary
    of two-tuples.
    :param train: If train is true, then return the ROC coordinates for the training data.
    :param valid: If valid is true, then return the ROC coordinates for the validation data.
    :param xval: If xval is true, then return the ROC coordinates for the cross validation data.
    :return rocs_cooridinates: the true cooridinates of the roc curve.
    """
    tm = ModelBase._get_metrics(self, train, valid, xval)
    m = {}
    for k,v in zip(tm.keys(),tm.values()):
      if v is not None:
        m[k] = (v.fprs, v.tprs)
    return m.values()[0] if len(m) == 1 else m



  def confusion_matrix(self, metrics=None, thresholds=None, train=False, valid=False, xval=False):
    """
    Get the confusion matrix for the specified metrics/thresholds
    If all are False (default), then return the training metric value.
    If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
    and "xval"

    :param metrics: A string (or list of strings) in {"min_per_class_accuracy", "absolute_MCC", "tnr", "fnr", "fpr", "tpr", "precision", "accuracy", "f0point5", "f2", "f1"}
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :param train: If train is True, then return the confusion matrix value for the training data.
    :param valid: If valid is True, then return the confusion matrix value for the validation data.
    :param xval:  If xval is True, then return the confusion matrix value for the cross validation data.
    :return: The confusion matrix for this binomial model.
    """
    tm = ModelBase._get_metrics(self, train, valid, xval)
    m = {}
    for k,v in zip(tm.keys(),tm.values()): m[k] = None if v is None else v.confusion_matrix(metrics=metrics, thresholds=thresholds)
    return m.values()[0] if len(m) == 1 else m

  def find_threshold_by_max_metric(self,metric,train=False, valid=False, xval=False):
    """
    If all are False (default), then return the training metric value.
    If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
    and "xval"

    :param train: If train is True, then return the threshold_by_max_metric value for the training data.
    :param valid: If valid is True, then return the threshold_by_max_metric value for the validation data.
    :param xval:  If xval is True, then return the threshold_by_max_metric value for the cross validation data.
    :return: The threshold_by_max_metric for this binomial model.
    """
    tm = ModelBase._get_metrics(self, train, valid, xval)
    m = {}
    for k,v in zip(tm.keys(),tm.values()): m[k] = None if v is None else v.find_threshold_by_max_metric(metric)
    return m.values()[0] if len(m) == 1 else m

  def find_idx_by_threshold(self,threshold,train=False, valid=False, xval=False):
    """
    Retrieve the index in this metric's threshold list at which the given threshold is located.
    If all are False (default), then return the training metric value.
    If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
    and "xval"

    :param train: If train is True, then return the idx_by_threshold for the training data.
    :param valid: If valid is True, then return the idx_by_threshold for the validation data.
    :param xval:  If xval is True, then return the idx_by_threshold for the cross validation data.
    :return: The idx_by_threshold for this binomial model.
    """
    tm = ModelBase._get_metrics(self, train, valid, xval)
    m = {}
    for k,v in zip(tm.keys(),tm.values()): m[k] = None if v is None else v.find_idx_by_threshold(threshold)
    return m.values()[0] if len(m) == 1 else m