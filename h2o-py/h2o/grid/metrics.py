
#classes for grid search

class H2OAutoEncoderGridSearch():

  def anomaly(self,test_data,per_feature=False):
    """Obtain the reconstruction error for the input test_data.

    Parameters
    ----------
      test_data : H2OFrame
        The dataset upon which the reconstruction error is computed.
      per_feature : bool
        Whether to return the square reconstruction error per feature. Otherwise, return
        the mean square error.

    Returns
    -------
      Return the reconstruction error.
    """
    return {model.model_id:model.anomaly(test_data,per_feature) for model in self.models}

class H2OBinomialGridSearch():

  def F1(self, thresholds=None, train=False, valid=False, xval=False):
    """Get the F1 values for a set of thresholds for the models explored

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
      Dictionary of model keys to F1 values
    """
    return {model.model_id:model.F1(thresholds, train, valid, xval) for model in self.models}# dict model key -> F1 score

  def F2(self, thresholds=None, train=False, valid=False, xval=False):
    """Get the F2 for a set of thresholds.
    If all are False (default), then return the training metric value.
    If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
    and "xval"

    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :param train: If train is True, then return the F2 value for the training data.
    :param valid: If valid is True, then return the F2 value for the validation data.
    :param xval:  If xval is True, then return the F2 value for the cross validation data.
    :return: Dictionary of model keys to F2 values.
    """
    return {model.model_id:model.F2(thresholds, train, valid, xval) for model in self.models}

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
    return {model.model_id:model.F0point5(thresholds, train, valid, xval) for model in self.models}

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
    return {model.model_id:model.accuracy(thresholds, train, valid, xval) for model in self.models}

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
    return {model.model_id:model.error(thresholds, train, valid, xval) for model in self.models}

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
    return {model.model_id:model.precision(thresholds, train, valid, xval) for model in self.models}

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
    return {model.model_id:model.tpr(thresholds, train, valid, xval) for model in self.models}


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
    return {model.model_id:model.tnr(thresholds, train, valid, xval) for model in self.models}


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
    return {model.model_id:model.fnr(thresholds, train, valid, xval) for model in self.models}


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
    return {model.model_id:model.fpr(thresholds, train, valid, xval) for model in self.models}


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
    return {model.model_id:model.recall(thresholds, train, valid, xval) for model in self.models}


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
    return {model.model_id:model.sensitivity(thresholds, train, valid, xval) for model in self.models}


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
    return {model.model_id:model.fallout(thresholds, train, valid, xval) for model in self.models}

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
    return {model.model_id:model.missrate(thresholds, train, valid, xval) for model in self.models}

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
    return {model.model_id:model.specificity(thresholds, train, valid, xval) for model in self.models}


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
    return {model.model_id:model.mcc(thresholds, train, valid, xval) for model in self.models}


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
    return {model.model_id:model.max_per_class_error(thresholds, train, valid, xval) for model in self.models}


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
    return {model.model_id:model.metric(metric, thresholds, train, valid, xval) for model in self.models}


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
    return {model.model_id:model.roc(train, valid, xval) for model in self.models}



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
    return {model.model_id:model.confusion_matrix(metrics, thresholds, train, valid, xval) for model in self.models}


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
    return {model.model_id:model.find_threshold_by_max_metric(metric, train, valid, xval) for model in self.models}

  def find_idx_by_threshold(self, threshold, train=False, valid=False, xval=False):
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
    return {model.model_id:model.find_idx_by_threshold(threshold, train, valid, xval) for model in self.models}

class H2OClusteringGridSearch():
  def size(self, train=False, valid=False, xval=False):
    """
    Get the sizes of each cluster.

    If all are False (default), then return the training metric value.
    If more than one options is set to True, then return a dictionary of metrics where
    the keys are "train", "valid", and "xval"

    Parameters
    ----------
      train : bool, optional
        If True, then return cluster sizes for the training data.
      valid : bool, optional
        If True, then return the cluster sizes for the validation data.
      xval : bool, optional
        If True, then return the cluster sizes for each of the cross-validated splits.

    Returns
    -------
      Returns the cluster sizes for the specified key(s).
    """
    return {model.model_id:model.size(train, valid, xval) for model in self.models}

  def num_iterations(self):
    """
    Get the number of iterations that it took to converge or reach max iterations.

    Returns
    -------
      The number of iterations (integer).
    """
    return {model.model_id:model.num_iterations() for model in self.models}


  def betweenss(self, train=False, valid=False, xval=False):
    """
    Get the between cluster sum of squares.

    If all are False (default), then return the training metric value.
    If more than one options is set to True, then return a dictionary of metrics where
    the keys are "train", "valid", and "xval".

    Parameters
    ----------
      train : bool, optional
        If True, then return the between cluster sum of squares value for the
        training data.
      valid : bool, optional
        If True, then return the between cluster sum of squares value for the
        validation data.
      xval : bool, optional
        If True, then return the between cluster sum of squares value for each of
        the cross-validated splits.

    Returns
    -------
      Returns the between sum of squares values for the specified key(s).
    """
    return {model.model_id:model.betweenss(train, valid, xval) for model in self.models}


  def totss(self, train=False, valid=False, xval=False):
    """
    Get the total sum of squares.

    If all are False (default), then return the training metric value.
    If more than one options is set to True, then return a dictionary of metrics where
    the keys are "train", "valid", and "xval".

    Parameters
    ----------
      train : bool, optional
        If True, then return the total sum of squares value for the training
        data.
      valid : bool, optional
        If True, then return the total sum of squares value for the validation
        data.
      xval : bool, optional
        If True, then return the total sum of squares value for each of the
        cross-validated splits.

    Returns
    -------
      Returns the total sum of squares values for the specified key(s).
    """
    return {model.model_id:model.totss(train, valid, xval) for model in self.models}


  def tot_withinss(self, train=False, valid=False, xval=False):
    """
    Get the total within cluster sum of squares.

    If all are False (default), then return the training metric value.
    If more than one options is set to True, then return a dictionary of metrics where
    the keys are "train", "valid", and "xval".

    Parameters
    ----------
      train : bool, optional
        If True, then return the total within cluster sum of squares value for
        the training data.
      valid : bool, optional
        If True, then return the total within cluster sum of squares value for
        the validation data.
      xval : bool, optional
        If True, then return the total within cluster sum of squares value for
        each of the cross-validated splits.

    Returns
    -------
      Returns the total within cluster sum of squares values for the specified key(s).
    """
    return {model.model_id:model.tot_withinss(train, valid, xval) for model in self.models}


  def withinss(self, train=False, valid=False, xval=False):
    """
    Get the within cluster sum of squares for each cluster.

    If all are False (default), then return the training metric value.
    If more than one options is set to True, then return a dictionary of metrics where
    the keys are "train", "valid", and "xval".

    Parameters
    ----------
      train : bool, optional
        If True, then return the within cluster sum of squares value for the
        training data.
      valid : bool, optional
        If True, then return the within cluster sum of squares value for the
        validation data.
      xval : bool, optional
        If True, then return the within cluster sum of squares value for each of
        the cross-validated splits.

    Returns
    -------
      Returns the total sum of squares values for the specified key(s).
    """
    return {model.model_id:model.withinss(train, valid, xval) for model in self.models}


  def centroid_stats(self, train=False, valid=False, xval=False):
    """
    Get the centroid statistics for each cluster.

    If all are False (default), then return the training metric value.
    If more than one options is set to True, then return a dictionary of metrics where
    the keys are "train", "valid", and "xval".

    Parameters
    ----------
      train : bool, optional
        If True, then return the centroid statistics for the training data.
      valid : bool, optional
        If True, then return the centroid statistics for the validation data.
      xval : bool, optional
        If True, then return the centroid statistics for each of the cross-validated
        splits.

    Returns
    -------
      Returns the centroid statistics for the specified key(s).
    """
    return {model.model_id:model.centroid_stats(train, valid, xval) for model in self.models}


  def centers(self):
    """
    Returns
    -------
      The centers for the KMeans model.
    """
    return {model.model_id:model.centers() for model in self.models}


  def centers_std(self):
    """
    Returns
    -------
      The standardized centers for the kmeans model.
    """
    return {model.model_id:model.centers_std() for model in self.models}

class H2ODimReductionGridSearch():
  def num_iterations(self):
    """
    Get the number of iterations that it took to converge or reach max iterations.

    :return: number of iterations (integer)
    """
    return {model.model_id:model.num_iterations for model in self.models}

  def objective(self):
    """
    Get the final value of the objective function from the GLRM model.

    :return: final objective value (double)
    """
    return {model.model_id:model.objective for model in self.models}

  def final_step(self):
    """
    Get the final step size from the GLRM model.

    :return: final step size (double)
    """
    return {model.model_id:model.final_step for model in self.models}


  def archetypes(self):
    """
    :return: the archetypes (Y) of the GLRM model.
    """
    return {model.model_id:model.archetypes for model in self.models}

class H2OMultinomialGridSearch():

  def confusion_matrix(self, data):
    """
    Returns a confusion matrix based of H2O's default prediction threshold for a dataset
    """
    return {model.model_id:model.confusion_matrix(data) for model in self.models}

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
    return {model.model_id:model.hit_ratio_table(train, valid, xval) for model in self.models}

class H2ORegressionGridSearch():
  pass