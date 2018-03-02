# -*- encoding: utf-8 -*-
"""
Regression model.

:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import absolute_import, division, print_function, unicode_literals

import imp

from h2o.model.confusion_matrix import ConfusionMatrix
from h2o.utils.backward_compatibility import backwards_compatible
from h2o.utils.compatibility import *  # NOQA
from h2o.utils.typechecks import assert_is_type, assert_satisfies, numeric


class MetricsBase(backwards_compatible()):
    """
    A parent class to house common metrics available for the various Metrics types.

    The methods here are available across different model categories.
    """

    def __init__(self, metric_json, on=None, algo=""):
        super(MetricsBase, self).__init__()
        # Yep, it's messed up...
        if isinstance(metric_json, MetricsBase): metric_json = metric_json._metric_json
        self._metric_json = metric_json
        # train and valid and xval are not mutually exclusive -- could have a test. train and
        # valid only make sense at model build time.
        self._on_train = False
        self._on_valid = False
        self._on_xval = False
        self._algo = algo
        if on == "training_metrics":
            self._on_train = True
        elif on == "validation_metrics":
            self._on_valid = True
        elif on == "cross_validation_metrics":
            self._on_xval = True
        elif on is None:
            pass
        else:
            raise ValueError("on expected to be train,valid,or xval. Got: " + str(on))

    @classmethod
    def make(cls, kvs):
        """Factory method to instantiate a MetricsBase object from the list of key-value pairs."""
        return cls(metric_json=dict(kvs))

    def __repr__(self):
        # FIXME !!!  __repr__ should never print anything, but return a string
        self.show()
        return ""

    # TODO: convert to actual fields list
    def __getitem__(self, key):
        return self._metric_json.get(key)

    @staticmethod
    def _has(dictionary, key):
        return key in dictionary and dictionary[key] is not None

    def show(self):
        """Display a short summary of the metrics."""
        metric_type = self._metric_json['__meta']['schema_type']
        types_w_glm = ['ModelMetricsRegressionGLM', 'ModelMetricsBinomialGLM']
        types_w_clustering = ['ModelMetricsClustering']
        types_w_mult = ['ModelMetricsMultinomial']
        types_w_ord = ['ModelMetricsOrdinal']
        types_w_bin = ['ModelMetricsBinomial', 'ModelMetricsBinomialGLM']
        types_w_r2 = ['ModelMetricsRegressionGLM']
        types_w_mean_residual_deviance = ['ModelMetricsRegressionGLM', 'ModelMetricsRegression']
        types_w_mean_absolute_error = ['ModelMetricsRegressionGLM', 'ModelMetricsRegression']
        types_w_logloss = types_w_bin + types_w_mult+types_w_ord
        types_w_dim = ["ModelMetricsGLRM"]

        print()
        print(metric_type + ": " + self._algo)
        reported_on = "** Reported on {} data. **"
        if self._on_train:
            print(reported_on.format("train"))
        elif self._on_valid:
            print(reported_on.format("validation"))
        elif self._on_xval:
            print(reported_on.format("cross-validation"))
        else:
            print(reported_on.format("test"))
        print()
        print("MSE: " + str(self.mse()))
        print("RMSE: " + str(self.rmse()))
        if metric_type in types_w_mean_absolute_error:
            print("MAE: " + str(self.mae()))
            print("RMSLE: " + str(self.rmsle()))
        if metric_type in types_w_r2:
            print("R^2: " + str(self.r2()))
        if metric_type in types_w_mean_residual_deviance:
            print("Mean Residual Deviance: " + str(self.mean_residual_deviance()))
        if metric_type in types_w_logloss:
            print("LogLoss: " + str(self.logloss()))
        if metric_type == 'ModelMetricsBinomial':
            # second element for first threshold is the actual mean per class error
            print("Mean Per-Class Error: %s" % self.mean_per_class_error()[0][1])
        if metric_type == 'ModelMetricsMultinomial' or metric_type == 'ModelMetricsOrdinal':
            print("Mean Per-Class Error: " + str(self.mean_per_class_error()))
        if metric_type in types_w_glm:
            print("Null degrees of freedom: " + str(self.null_degrees_of_freedom()))
            print("Residual degrees of freedom: " + str(self.residual_degrees_of_freedom()))
            print("Null deviance: " + str(self.null_deviance()))
            print("Residual deviance: " + str(self.residual_deviance()))
            print("AIC: " + str(self.aic()))
        if metric_type in types_w_bin:
            print("AUC: " + str(self.auc()))
            print("Gini: " + str(self.gini()))
            self.confusion_matrix().show()
            self._metric_json["max_criteria_and_metric_scores"].show()
            if self.gains_lift():
                print(self.gains_lift())

        if (metric_type in types_w_mult) or (metric_type in types_w_ord):
            self.confusion_matrix().show()
            self.hit_ratio_table().show()
        if metric_type in types_w_clustering:
            print("Total Within Cluster Sum of Square Error: " + str(self.tot_withinss()))
            print("Total Sum of Square Error to Grand Mean: " + str(self.totss()))
            print("Between Cluster Sum of Square Error: " + str(self.betweenss()))
            self._metric_json['centroid_stats'].show()

        if metric_type in types_w_dim:
            print("Sum of Squared Error (Numeric): " + str(self.num_err()))
            print("Misclassification Error (Categorical): " + str(self.cat_err()))
        if self.custom_metric_name():
            print("{}: {}".format(self.custom_metric_name(), self.custom_metric_value()))


    def r2(self):
        """The R squared coefficient."""
        return self._metric_json["r2"]


    def logloss(self):
        """Log loss."""
        return self._metric_json["logloss"]


    def nobs(self):
        """The number of observations."""
        return self._metric_json["nobs"]


    def mean_residual_deviance(self):
        """The mean residual deviance for this set of metrics."""
        return self._metric_json["mean_residual_deviance"]


    def auc(self):
        """The AUC for this set of metrics."""
        return self._metric_json['AUC']


    def aic(self):
        """The AIC for this set of metrics."""
        return self._metric_json['AIC']


    def gini(self):
        """Gini coefficient."""
        return self._metric_json['Gini']


    def mse(self):
        """The MSE for this set of metrics."""
        return self._metric_json['MSE']


    def rmse(self):
        """The RMSE for this set of metrics."""
        return self._metric_json['RMSE']


    def mae(self):
        """The MAE for this set of metrics."""
        return self._metric_json['mae']


    def rmsle(self):
        """The RMSLE for this set of metrics."""
        return self._metric_json['rmsle']


    def residual_deviance(self):
        """The residual deviance if the model has it, otherwise None."""
        if MetricsBase._has(self._metric_json, "residual_deviance"):
            return self._metric_json["residual_deviance"]
        return None


    def residual_degrees_of_freedom(self):
        """The residual DoF if the model has residual deviance, otherwise None."""
        if MetricsBase._has(self._metric_json, "residual_degrees_of_freedom"):
            return self._metric_json["residual_degrees_of_freedom"]
        return None


    def null_deviance(self):
        """The null deviance if the model has residual deviance, otherwise None."""
        if MetricsBase._has(self._metric_json, "null_deviance"):
            return self._metric_json["null_deviance"]
        return None


    def null_degrees_of_freedom(self):
        """The null DoF if the model has residual deviance, otherwise None."""
        if MetricsBase._has(self._metric_json, "null_degrees_of_freedom"):
            return self._metric_json["null_degrees_of_freedom"]
        return None


    def mean_per_class_error(self):
        """The mean per class error."""
        return self._metric_json['mean_per_class_error']

    def custom_metric_name(self):
        """Name of custom metric or None."""
        if MetricsBase._has(self._metric_json, "custom_metric_name"):
            return self._metric_json['custom_metric_name']
        else:
            return None

    def custom_metric_value(self):
        """Value of custom metric or None."""        
        if MetricsBase._has(self._metric_json, "custom_metric_value"):
            return self._metric_json['custom_metric_value']
        else:
            return None

    # Deprecated functions; left here for backward compatibility
    _bcim = {
        "giniCoef": lambda self, *args, **kwargs: self.gini(*args, **kwargs)
    }


class H2ORegressionModelMetrics(MetricsBase):
    """
    This class provides an API for inspecting the metrics returned by a regression model.

    It is possible to retrieve the R^2 (1 - MSE/variance) and MSE.
    """

    def __init__(self, metric_json, on=None, algo=""):
        super(H2ORegressionModelMetrics, self).__init__(metric_json, on, algo)




class H2OClusteringModelMetrics(MetricsBase):

    def __init__(self, metric_json, on=None, algo=""):
        super(H2OClusteringModelMetrics, self).__init__(metric_json, on, algo)


    def tot_withinss(self):
        """The Total Within Cluster Sum-of-Square Error, or None if not present."""
        if MetricsBase._has(self._metric_json, "tot_withinss"):
            return self._metric_json["tot_withinss"]
        return None


    def totss(self):
        """The Total Sum-of-Square Error to Grand Mean, or None if not present."""
        if MetricsBase._has(self._metric_json, "totss"):
            return self._metric_json["totss"]
        return None

    def betweenss(self):
        """The Between Cluster Sum-of-Square Error, or None if not present."""
        if MetricsBase._has(self._metric_json, "betweenss"):
            return self._metric_json["betweenss"]
        return None




class H2OMultinomialModelMetrics(MetricsBase):

    def __init__(self, metric_json, on=None, algo=""):
        super(H2OMultinomialModelMetrics, self).__init__(metric_json, on, algo)

    def confusion_matrix(self):
        """Returns a confusion matrix based of H2O's default prediction threshold for a dataset."""
        return self._metric_json['cm']['table']


    def hit_ratio_table(self):
        """Retrieve the Hit Ratios."""
        return self._metric_json['hit_ratio_table']



class H2OOrdinalModelMetrics(MetricsBase):

    def __init__(self, metric_json, on=None, algo=""):
        super(H2OOrdinalModelMetrics, self).__init__(metric_json, on, algo)

    def confusion_matrix(self):
        """Returns a confusion matrix based of H2O's default prediction threshold for a dataset."""
        return self._metric_json['cm']['table']


    def hit_ratio_table(self):
        """Retrieve the Hit Ratios."""
        return self._metric_json['hit_ratio_table']

class H2OBinomialModelMetrics(MetricsBase):
    """
    This class is essentially an API for the AUC object.
    This class contains methods for inspecting the AUC for different criteria.
    To input the different criteria, use the static variable `criteria`.
    """

    def __init__(self, metric_json, on=None, algo=""):
        """
          Create a new Binomial Metrics object (essentially a wrapper around some json)

          :param metric_json: A blob of json holding all of the needed information
          :param on_train: Metrics built on training data (default is False)
          :param on_valid: Metrics built on validation data (default is False)
          :param on_xval: Metrics built on cross validation data (default is False)
          :param algo: The algorithm the metrics are based off of (e.g. deeplearning, gbm, etc.)
          :returns: A new H2OBinomialModelMetrics object.
          """
        super(H2OBinomialModelMetrics, self).__init__(metric_json, on, algo)


    def F1(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the
            thresholds in this set of metrics will be used.
        :returns: The F1 for the given set of thresholds.
        """
        return self.metric("f1", thresholds=thresholds)


    def F2(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the
            thresholds in this set of metrics will be used.
        :returns: The F2 for this set of metrics and thresholds.
        """
        return self.metric("f2", thresholds=thresholds)


    def F0point5(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the
            thresholds in this set of metrics will be used.
        :returns: The F0.5 for this set of metrics and thresholds.
        """
        return self.metric("f0point5", thresholds=thresholds)


    def accuracy(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the
            thresholds in this set of metrics will be used.
        :returns: The accuracy for this set of metrics and thresholds.
        """
        return self.metric("accuracy", thresholds=thresholds)


    def error(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the
            thresholds in this set of metrics will be used.
        :returns: The error for this set of metrics and thresholds.
        """
        return 1 - self.metric("accuracy", thresholds=thresholds)


    def precision(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the
            thresholds in this set of metrics will be used.
        :returns: The precision for this set of metrics and thresholds.
        """
        return self.metric("precision", thresholds=thresholds)


    def tpr(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the
            thresholds in this set of metrics will be used.
        :returns: The True Postive Rate.
        """
        return self.metric("tpr", thresholds=thresholds)


    def tnr(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the
            thresholds in this set of metrics will be used.
        :returns: The True Negative Rate.
        """
        return self.metric("tnr", thresholds=thresholds)


    def fnr(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the
            thresholds in this set of metrics will be used.
        :returns: The False Negative Rate.
        """
        return self.metric("fnr", thresholds=thresholds)


    def fpr(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the
            thresholds in this set of metrics will be used.
        :returns: The False Positive Rate.
        """
        return self.metric("fpr", thresholds=thresholds)


    def recall(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the
            thresholds in this set of metrics will be used.
        :returns: Recall for this set of metrics and thresholds.
        """
        return self.metric("tpr", thresholds=thresholds)


    def sensitivity(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the
            thresholds in this set of metrics will be used.
        :returns: Sensitivity or True Positive Rate for this set of metrics and thresholds.
        """
        return self.metric("tpr", thresholds=thresholds)


    def fallout(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the
            thresholds in this set of metrics will be used.
        :returns: The fallout (same as False Positive Rate) for this set of metrics and thresholds.
        """
        return self.metric("fpr", thresholds=thresholds)


    def missrate(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the
            thresholds in this set of metrics will be used.
        :returns: THe miss rate (same as False Negative Rate).
        """
        return self.metric("fnr", thresholds=thresholds)


    def specificity(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the
            thresholds in this set of metrics will be used.
        :returns: The specificity (same as True Negative Rate).
        """
        return self.metric("tnr", thresholds=thresholds)


    def mcc(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the
            thresholds in this set of metrics will be used.
        :returns: The absolute MCC (a value between 0 and 1, 0 being totally dissimilar, 1 being identical).
        """
        return self.metric("absolute_mcc", thresholds=thresholds)


    def max_per_class_error(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the
            thresholds in this set of metrics will be used.
        :returns: Return 1 - min(per class accuracy).
        """
        return 1 - self.metric("min_per_class_accuracy", thresholds=thresholds)


    def mean_per_class_error(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then the
            thresholds in this set of metrics will be used.
        :returns: mean per class error.
        """
        return [[x[0], 1 - x[1]] for x in self.metric("mean_per_class_accuracy", thresholds=thresholds)]


    def metric(self, metric, thresholds=None):
        """
        :param str metric: The desired metric.
        :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]). If None, then
            the thresholds in this set of metrics will be used.
        :returns: The set of metrics for the list of thresholds.
        """
        assert_is_type(thresholds, None, [numeric])
        if not thresholds: thresholds = [self.find_threshold_by_max_metric(metric)]
        thresh2d = self._metric_json['thresholds_and_metric_scores']
        metrics = []
        for t in thresholds:
            idx = self.find_idx_by_threshold(t)
            metrics.append([t, thresh2d[metric][idx]])
        return metrics


    def plot(self, type="roc", server=False):
        """
        Produce the desired metric plot.

        :param type: the type of metric plot (currently, only ROC supported).
        :param server: if True, generate plot inline using matplotlib's "Agg" backend.
        :returns: None
        """
        # TODO: add more types (i.e. cutoffs)
        assert_is_type(type, "roc")
        # check for matplotlib. exit if absent.
        try:
            imp.find_module('matplotlib')
            import matplotlib
            if server: matplotlib.use('Agg', warn=False)
            import matplotlib.pyplot as plt
        except ImportError:
            print("matplotlib is required for this function!")
            return

        if type == "roc":
            plt.xlabel('False Positive Rate (FPR)')
            plt.ylabel('True Positive Rate (TPR)')
            plt.title('ROC Curve')
            plt.text(0.5, 0.5, r'AUC={0:.4f}'.format(self._metric_json["AUC"]))
            plt.plot(self.fprs, self.tprs, 'b--')
            plt.axis([0, 1, 0, 1])
            if not server: plt.show()


    @property
    def fprs(self):
        """
        Return all false positive rates for all threshold values.

        :returns: a list of false positive rates.
        """
        return self._metric_json["thresholds_and_metric_scores"]["fpr"]


    @property
    def tprs(self):
        """
        Return all true positive rates for all threshold values.

        :returns: a list of true positive rates.
        """
        return self._metric_json["thresholds_and_metric_scores"]["tpr"]


    def confusion_matrix(self, metrics=None, thresholds=None):
        """
        Get the confusion matrix for the specified metric

        :param metrics: A string (or list of strings) in {"min_per_class_accuracy", "absolute_mcc", "tnr", "fnr", "fpr",
            "tpr", "precision", "accuracy", "f0point5", "f2", "f1","mean_per_class_accuracy"}
        :param thresholds: A value (or list of values) between 0 and 1
        :returns: a list of ConfusionMatrix objects (if there are more than one to return), or a single ConfusionMatrix
            (if there is only one).
        """
        # make lists out of metrics and thresholds arguments
        if metrics is None and thresholds is None: metrics = ["f1"]

        if isinstance(metrics, list):
            metrics_list = metrics
        elif metrics is None:
            metrics_list = []
        else:
            metrics_list = [metrics]

        if isinstance(thresholds, list):
            thresholds_list = thresholds
        elif thresholds is None:
            thresholds_list = []
        else:
            thresholds_list = [thresholds]

        # error check the metrics_list and thresholds_list
        assert_is_type(thresholds_list, [numeric])
        assert_satisfies(thresholds_list, all(0 <= t <= 1 for t in thresholds_list))

        if not all(m.lower() in ["min_per_class_accuracy", "absolute_mcc", "precision", "recall", "specificity",
                                 "accuracy", "f0point5", "f2", "f1", "mean_per_class_accuracy"] for m in metrics_list):
            raise ValueError(
                "The only allowable metrics are min_per_class_accuracy, absolute_mcc, precision, accuracy, f0point5, "
                "f2, f1, mean_per_class_accuracy")

        # make one big list that combines the thresholds and metric-thresholds
        metrics_thresholds = [self.find_threshold_by_max_metric(m) for m in metrics_list]
        for mt in metrics_thresholds:
            thresholds_list.append(mt)

        thresh2d = self._metric_json['thresholds_and_metric_scores']
        actual_thresholds = [float(e[0]) for i, e in enumerate(thresh2d.cell_values)]
        cms = []
        for t in thresholds_list:
            idx = self.find_idx_by_threshold(t)
            row = thresh2d.cell_values[idx]
            tns = row[11]
            fns = row[12]
            fps = row[13]
            tps = row[14]
            p = tps + fns
            n = tns + fps
            c0 = n - fps
            c1 = p - tps
            if t in metrics_thresholds:
                m = metrics_list[metrics_thresholds.index(t)]
                table_header = "Confusion Matrix (Act/Pred) for max " + m + " @ threshold = " + str(
                    actual_thresholds[idx])
            else:
                table_header = "Confusion Matrix (Act/Pred) @ threshold = " + str(actual_thresholds[idx])
            cms.append(ConfusionMatrix(cm=[[c0, fps], [c1, tps]], domains=self._metric_json['domain'],
                                       table_header=table_header))

        if len(cms) == 1:
            return cms[0]
        else:
            return cms


    def find_threshold_by_max_metric(self, metric):
        """
        :param metric: A string in {"min_per_class_accuracy", "absolute_mcc", "precision", "recall", "specificity",
            "accuracy", "f0point5", "f2", "f1", "mean_per_class_accuracy"}.
        :returns: the threshold at which the given metric is maximal.
        """
        crit2d = self._metric_json['max_criteria_and_metric_scores']

        for e in crit2d.cell_values:
            if e[0] == "max " + metric.lower():
                return e[1]
        raise ValueError("No metric " + str(metric.lower()))


    def find_idx_by_threshold(self, threshold):
        """
        Retrieve the index in this metric's threshold list at which the given threshold is located.

        :param threshold: Find the index of this input threshold.
        :returns: the index
        :raises ValueError: if no such index can be found.
        """
        assert_is_type(threshold, numeric)
        thresh2d = self._metric_json['thresholds_and_metric_scores']
        for i, e in enumerate(thresh2d.cell_values):
            t = float(e[0])
            if abs(t - threshold) < 0.00000001 * max(t, threshold):
                return i
        if threshold >= 0 and threshold <= 1:
            thresholds = [float(e[0]) for i, e in enumerate(thresh2d.cell_values)]
            threshold_diffs = [abs(t - threshold) for t in thresholds]
            closest_idx = threshold_diffs.index(min(threshold_diffs))
            closest_threshold = thresholds[closest_idx]
            print("Could not find exact threshold {0}; using closest threshold found {1}."
                  .format(threshold, closest_threshold))
            return closest_idx
        raise ValueError("Threshold must be between 0 and 1, but got {0} ".format(threshold))


    def gains_lift(self):
        """Retrieve the Gains/Lift table."""
        if 'gains_lift_table' in self._metric_json:
            return self._metric_json['gains_lift_table']
        return None




class H2OAutoEncoderModelMetrics(MetricsBase):

    def __init__(self, metric_json, on=None, algo=""):
        super(H2OAutoEncoderModelMetrics, self).__init__(metric_json, on, algo)




class H2ODimReductionModelMetrics(MetricsBase):

    def __init__(self, metric_json, on=None, algo=""):
        super(H2ODimReductionModelMetrics, self).__init__(metric_json, on, algo)

    def num_err(self):
        """Sum of Squared Error over non-missing numeric entries, or None if not present."""
        if MetricsBase._has(self._metric_json, "numerr"):
            return self._metric_json["numerr"]
        return None

    def cat_err(self):
        """The Number of Misclassified categories over non-missing categorical entries, or None if not present."""
        if MetricsBase._has(self._metric_json, "caterr"):
            return self._metric_json["caterr"]
        return None




class H2OWordEmbeddingModelMetrics(MetricsBase):

    def __init__(self, metric_json, on=None, algo=""):
        super(H2OWordEmbeddingModelMetrics, self).__init__(metric_json, on, algo)
