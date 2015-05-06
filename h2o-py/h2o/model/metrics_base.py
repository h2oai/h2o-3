
from model_base import ModelBase
from h2o.model.confusion_matrix import ConfusionMatrix

class MetricsBase(object):
  """
  A parent class to house common metrics available for the various Metrics types.

  The methods here are available acorss different model categories, and so appear here.
  """
  def __init__(self, metric_json,on_train,on_valid,algo):
    self._metric_json = metric_json
    self._on_train = on_train   # train and valid are not mutually exclusive -- could have a test. train and valid only make sense at model build time.
    self._on_valid = on_valid
    self._algo = algo

  def __repr__(self):
    self.show()
    return ""

  def show(self):
    """
    Display a short summary of the metrics.
    :return: None
    """
    metric_type = self._metric_json['__meta']['schema_type']
    types_w_glm =        ['ModelMetricsRegressionGLM', 'ModelMetricsBinomialGLM']
    types_w_clustering = ['ModelMetricsClustering']
    types_w_mult =       ['ModelMetricsMultinomial']
    types_w_bin =        ['ModelMetricsBinomial', 'ModelMetricsBinomialGLM']
    types_w_r2 =         ['ModelMetricsBinomial', 'ModelMetricsRegression'] + types_w_glm + types_w_mult
    types_w_logloss =    types_w_bin + types_w_mult

    print
    print metric_type + ": " + self._algo
    reported_on = "** Reported on {} data. **"
    if self._on_train:
      print reported_on.format("train")
    elif self._on_valid:
      print reported_on.format("validation")
    else:
      print reported_on.format("test")
    print
    print   "MSE: "                                           + str(self.mse())
    if metric_type in types_w_r2:
      print "R^2: "                                           + str(self.r2())
    if metric_type in types_w_logloss:
      print "LogLoss: "                                       + str(self.logloss())
    if metric_type in types_w_glm:
      print "Null degrees of freedom: "                       + str(self.null_degrees_of_freedom())
      print "Residual degrees of freedom: "                   + str(self.residual_degrees_of_freedom())
      print "Null deviance: "                                 + str(self.null_deviance())
      print "Residual deviance: "                             + str(self.residual_deviance())
      print "AIC: "                                           + str(self.aic())
    if metric_type in types_w_bin:
      print "AUC: "                                           + str(self.auc())
      print "Gini: "                                          + str(self.giniCoef())
      ConfusionMatrix(cm=self.confusion_matrices()[0], domains=self._metric_json['domain']).show()
      print                                                     self._metric_json["max_criteria_and_metric_scores"]
    if metric_type in types_w_mult:
      print                                                     self._metric_json['cm']['table']
      print                                                     self._metric_json['hit_ratio_table']
    if metric_type in types_w_clustering:
      print "Total Within Cluster Sum of Square Error: "      + str(self.tot_withinss())
      print "Total Sum of Square Error to Grand Mean: "       + str(self.totss())
      print "Between Cluster Sum of Square Error: "           + str(self.betweenss())
      self._metric_json['centroid_stats'].show()

  def r2(self):
    """
    :return: Retrieve the R^2 coefficient for this set of metrics
    """
    return self._metric_json["r2"]

  def logloss(self):
    """
    :return: Retrieve the log loss for this set of metrics.
    """
    return self._metric_json["logloss"]

  def auc(self):
    """
    :return: Retrieve the AUC for this set of metrics.
    """
    return self._metric_json['AUC']

  def aic(self):
    """
    :return: Retrieve the AIC for this set of metrics.
    """
    return self._metric_json['AIC']

  def giniCoef(self):
    """
    :return: Retrieve the Gini coefficeint for this set of metrics.
    """
    return self._metric_json['Gini']

  def mse(self):
    """
    :return: Retrieve the MSE for this set of metrics
    """
    return self._metric_json['MSE']

  def residual_deviance(self):
    """
    :return: the residual deviance if the model has residual deviance, or None if no residual deviance.
    """
    if ModelBase._has(self._metric_json, "residual_deviance"):
      return self._metric_json["residual_deviance"]
    return None

  def residual_degrees_of_freedom(self):
    """
    :return: the residual dof if the model has residual deviance, or None if no residual dof.
    """
    if ModelBase._has(self._metric_json, "residual_degrees_of_freedom"):
      return self._metric_json["residual_degrees_of_freedom"]
    return None

  def null_deviance(self):
    """
    :return: the null deviance if the model has residual deviance, or None if no null deviance.
    """
    if ModelBase._has(self._metric_json, "null_deviance"):
      return self._metric_json["null_deviance"]
    return None

  def null_degrees_of_freedom(self):
    """
    :return: the null dof if the model has residual deviance, or None if no null dof.
    """
    if ModelBase._has(self._metric_json, "null_degrees_of_freedom"):
      return self._metric_json["null_degrees_of_freedom"]
    return None

class H2ORegressionModelMetrics(MetricsBase):
  """
  This class provides an API for inspecting the metrics returned by a regression model.

  It is possible to retrieve the R^2 (1 - MSE/variance) and MSE
  """
  def __init__(self,metric_json,on_train=False,on_valid=False,algo=""):
    super(H2ORegressionModelMetrics, self).__init__(metric_json, on_train, on_valid, algo)


class H2OClusteringModelMetrics(MetricsBase):
  def __init__(self, metric_json, on_train=False, on_valid=False, algo=""):
    super(H2OClusteringModelMetrics, self).__init__(metric_json, on_train, on_valid, algo)

  def tot_withinss(self):
    """
    :return: the Total Within Cluster Sum-of-Square Error, or None if not present.
    """
    if ModelBase._has(self._metric_json, "tot_withinss"):
      return self._metric_json["tot_withinss"]
    return None

  def totss(self):
    """
    :return: the Total Sum-of-Square Error to Grand Mean, or None if not present.
    """
    if ModelBase._has(self._metric_json, "totss"):
      return self._metric_json["totss"]
    return None

  def betweenss(self):
    """
    :return: the Between Cluster Sum-of-Square Error, or None if not present.
    """
    if ModelBase._has(self._metric_json, "betweenss"):
      return self._metric_json["betweenss"]
    return None

class H2OMultinomialModelMetrics(MetricsBase):
  def __init__(self, metric_json, on_train=False, on_valid=False, algo=""):
    super(H2OMultinomialModelMetrics, self).__init__(metric_json, on_train, on_valid,algo)

class H2OBinomialModelMetrics(MetricsBase):
  """
  This class is essentially an API for the AUC object.
  This class contains methods for inspecting the AUC for different criteria.
  To input the different criteria, use the static variable `criteria`
  """

  def __init__(self, metric_json, on_train=False, on_valid=False, algo=""):
    """
      Create a new Binomial Metrics object (essentially a wrapper around some json)

      :param metric_json: A blob of json holding all of the needed information
      :param on_train: Metrics built on training data (default is False)
      :param on_valid: Metrics built on validation data (default is False)
      :param algo: The algorithm the metrics are based off of (e.g. deeplearning, gbm, etc.)
      :return: A new H2OBinomialModelMetrics object.
      """
    super(H2OBinomialModelMetrics, self).__init__(metric_json, on_train, on_valid, algo)

  def F1(self, thresholds=None):
    """
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The F1 for the given set of thresholds.
    """
    return self.metric("f1", thresholds=thresholds)

  def F2(self, thresholds=None):
    """
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The F2 for this set of metrics and thresholds
    """
    return self.metric("f2", thresholds=thresholds)

  def F0point5(self, thresholds=None):
    """
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The F0point5 for this set of metrics and thresholds.
    """
    return self.metric("f0point5", thresholds=thresholds)

  def accuracy(self, thresholds=None):
    """
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The accuracy for this set of metrics and thresholds
    """
    return self.metric("accuracy", thresholds=thresholds)

  def error(self, thresholds=None):
    """
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The error for this set of metrics and thresholds.
    """
    return self.metric("error", thresholds=thresholds)

  def precision(self, thresholds=None):
    """
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The precision for this set of metrics and thresholds.
    """
    return self.metric("precision", thresholds=thresholds)

  def tpr(self, thresholds=None):
    """
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The True Postive Rate
    """
    return self.metric("tpr", thresholds=thresholds)

  def tnr(self, thresholds=None):
    """
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The True Negative Rate
    """
    return self.metric("tnr", thresholds=thresholds)

  def fnr(self, thresholds=None):
    """
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The False Negative Rate
    """
    return self.metric("fnr", thresholds=thresholds)

  def fpr(self, thresholds=None):
    """
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The False Positive Rate
    """
    return self.metric("fpr", thresholds=thresholds)

  def recall(self, thresholds=None):
    """
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: Recall for this set of metrics and thresholds
    """
    return self.metric("tpr", thresholds=thresholds)

  def sensitivity(self, thresholds=None):
    """
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: Sensitivity or True Positive Rate for this set of metrics and thresholds
    """
    return self.metric("tpr", thresholds=thresholds)

  def fallout(self, thresholds=None):
    """
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The fallout or False Positive Rate for this set of metrics and thresholds
    """
    return self.metric("fpr", thresholds=thresholds)

  def missrate(self, thresholds=None):
    """
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: THe missrate or False Negative Rate.
    """
    return self.metric("fnr", thresholds=thresholds)

  def specificity(self, thresholds=None):
    """
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The specificity or True Negative Rate.
    """
    return self.metric("tnr", thresholds=thresholds)

  def mcc(self, thresholds=None):
    """
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The absolute MCC (a value between 0 and 1, 0 being totally dissimilar, 1 being identical)
    """
    return self.metric("absolute_MCC", thresholds=thresholds)

  def max_per_class_error(self, thresholds=None):
    """
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: Return 1 - min_per_class_accuracy
    """
    return 1-self.metric("min_per_class_accuracy", thresholds=thresholds)

  def metric(self, metric, thresholds=None):
    """
    :param metric: The desired metric
    :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the thresholds in this set of metrics will be used.
    :return: The set of metrics for the list of thresholds
    """
    if not thresholds: thresholds=[self.find_threshold_by_max_metric(metric)]
    if not isinstance(thresholds,list):
      raise ValueError("thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99])")
    thresh2d = self._metric_json['thresholds_and_metric_scores']
    midx = thresh2d.col_header.index(metric)
    metrics = []
    for t in thresholds:
      idx = self.find_idx_by_threshold(t)
      row = thresh2d.cell_values[idx]
      metrics.append([t,row[midx]])
    return metrics

  def confusion_matrices(self, thresholds=None):
    """
    Each threshold defines a confusion matrix. For each threshold in the thresholds list, return a 2x2 list.

    :param thresholds: A list of thresholds.
    :return: A list of 2x2-lists: [, ..., [ [tns,fps], [fns,tps] ], ..., ]
    """
    if not thresholds: thresholds=[self.find_threshold_by_max_metric("f1")]
    if not isinstance(thresholds,list):
      raise ValueError("thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99])")
    thresh2d = self._metric_json['thresholds_and_metric_scores']
    tidx = thresh2d.col_header.index('tps')
    fidx = thresh2d.col_header.index('fps')
    p = self._metric_json['max_criteria_and_metric_scores'].cell_values[tidx-1][2]
    n = self._metric_json['max_criteria_and_metric_scores'].cell_values[fidx-1][2]
    cms = []
    for t in thresholds:
      idx = self.find_idx_by_threshold(t)
      row = thresh2d.cell_values[idx]
      tps = row[tidx]
      fps = row[fidx]
      c0  = float("nan") if isinstance(n, str) or isinstance(fps, str) else n - fps
      c1  = float("nan") if isinstance(p, str) or isinstance(tps, str) else p - tps
      fps = float("nan") if isinstance(fps,str) else fps
      tps = float("nan") if isinstance(tps,str) else tps
      cms.append([[c0,fps],[c1,tps]])
    return cms

  def find_threshold_by_max_metric(self,metric):
    """
    :param metric: A string in {"min_per_class_accuracy", "absolute_MCC", "tnr", "fnr", "fpr", "tpr", "precision", "error", "accuracy", "f0point5", "f2", "f1"}
    :return: the threshold at which the given metric is maximum.
    """
    crit2d = self._metric_json['max_criteria_and_metric_scores']
    for e in crit2d.cell_values:
      if e[0]==metric:
        return e[1]
    raise ValueError("No metric "+str(metric))

  def find_idx_by_threshold(self,threshold):
    """
    Retrieve the index in this metric's threshold list at which the given threshold is located.

    :param threshold: Find the index of this input threshold.
    :return: Return the index or throw a ValueError if no such index can be found.
    """
    if not isinstance(threshold,float):
      raise ValueError("Expected a float but got a "+type(threshold))
    thresh2d = self._metric_json['thresholds_and_metric_scores']
    for i,e in enumerate(thresh2d.cell_values):
      t = float(e[0])
      if abs(t-threshold) < 0.00000001 * max(t,threshold):
        return i
    raise ValueError("No threshold "+str(threshold))

class H2OAutoEncoderModelMetrics(MetricsBase):
  def __init__(self, metric_json, on_train=False, on_valid=False, algo=""):
    super(H2OAutoEncoderModelMetrics, self).__init__(metric_json, on_train, on_valid,algo)
