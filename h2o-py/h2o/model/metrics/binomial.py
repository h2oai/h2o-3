from __future__ import division, absolute_import, print_function, unicode_literals

import h2o
from h2o.model import MetricsBase, ConfusionMatrix
from h2o.plot import get_matplotlib_pyplot, decorate_plot_result, RAISE_ON_FIGURE_ACCESS
from h2o.utils.metaclass import deprecated_params
from h2o.utils.shared_utils import List
from h2o.utils.typechecks import assert_is_type, numeric, is_type, assert_satisfies


class H2OBinomialModelMetrics(MetricsBase):
    """
    This class is essentially an API for the AUC object.
    This class contains methods for inspecting the AUC for different criteria.
    To input the different criteria, use the static variable ``criteria``.
    """
    def _str_items_custom(self):
        items = []
        cm = self.confusion_matrix()
        if cm: items.append(cm)
        mcms = self._metric_json["max_criteria_and_metric_scores"]  # create a method to access this if it is that useful!
        if mcms: items.append(mcms)
        gl = self.gains_lift()
        if gl: items.append(gl)
        return items

    def F1(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (e.g. ``[0.01, 0.5, 0.99]``).
            If None, then the threshold maximizing the metric will be used.
        :returns: The F1 for the given set of thresholds.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.F1()
        """
        return self.metric("f1", thresholds=thresholds)

    def F2(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (e.g. ``[0.01, 0.5, 0.99]``).
            If None, then the threshold maximizing the metric will be used.
        :returns: The F2 for this set of metrics and thresholds.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.F2()
        """
        return self.metric("f2", thresholds=thresholds)

    def F0point5(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (e.g. ``[0.01, 0.5, 0.99]``).
            If None, then the threshold maximizing the metric will be used.
        :returns: The F0.5 for this set of metrics and thresholds.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.F0point5()
        """
        return self.metric("f0point5", thresholds=thresholds)

    def accuracy(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (e.g. ``[0.01, 0.5, 0.99]``).
            If None, then the threshold maximizing the metric will be used.
        :returns: The accuracy for this set of metrics and thresholds.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.accuracy()
        """
        return self.metric("accuracy", thresholds=thresholds)

    def error(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (e.g. ``[0.01, 0.5, 0.99]``).
            If None, then the threshold minimizing the error will be used.
        :returns: The error for this set of metrics and thresholds.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.error()
        """
        return H2OBinomialModelMetrics._accuracy_to_error(self.metric("accuracy", thresholds=thresholds))

    def precision(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (e.g. ``[0.01, 0.5, 0.99]``).
            If None, then the threshold maximizing the metric will be used.
        :returns: The precision for this set of metrics and thresholds.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.precision()
        """
        return self.metric("precision", thresholds=thresholds)

    def tpr(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (e.g. ``[0.01, 0.5, 0.99]``).
            If None, then the threshold maximizing the metric will be used.
        :returns: The True Postive Rate.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.tpr()
        """
        return self.metric("tpr", thresholds=thresholds)

    def tnr(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (e.g. ``[0.01, 0.5, 0.99]``).
            If None, then the threshold maximizing the metric will be used.
        :returns: The True Negative Rate.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.tnr()
        """
        return self.metric("tnr", thresholds=thresholds)

    def fnr(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (e.g. ``[0.01, 0.5, 0.99]``).
            If None, then the threshold maximizing the metric will be used.
        :returns: The False Negative Rate.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.fnr()
        """
        return self.metric("fnr", thresholds=thresholds)

    def fpr(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (e.g. ``[0.01, 0.5, 0.99]``).
            If None, then the threshold maximizing the metric will be used.
        :returns: The False Positive Rate.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.fpr()
        """
        return self.metric("fpr", thresholds=thresholds)

    def recall(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (e.g. ``[0.01, 0.5, 0.99]``).
            If None, then the threshold maximizing the metric will be used.
        :returns: Recall for this set of metrics and thresholds.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.recall()
        """
        return self.metric("recall", thresholds=thresholds)

    def sensitivity(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (e.g. ``[0.01, 0.5, 0.99]``).
            If None, then the threshold maximizing the metric will be used.
        :returns: Sensitivity or True Positive Rate for this set of metrics and thresholds.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.sensitivity()
        """
        return self.metric("sensitivity", thresholds=thresholds)

    def fallout(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (e.g. ``[0.01, 0.5, 0.99]``).
            If None, then the threshold maximizing the metric will be used.
        :returns: The fallout (same as False Positive Rate) for this set of metrics and thresholds.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.fallout()

        """
        return self.metric("fallout", thresholds=thresholds)

    def missrate(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (e.g. ``[0.01, 0.5, 0.99]``).
            If None, then the threshold maximizing the metric will be used.
        :returns: The miss rate (same as False Negative Rate).

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.missrate()
        """
        return self.metric("missrate", thresholds=thresholds)

    def specificity(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (e.g. ``[0.01, 0.5, 0.99]``).
            If None, then the threshold maximizing the metric will be used.
        :returns: The specificity (same as True Negative Rate).

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.specificity()
        """
        return self.metric("specificity", thresholds=thresholds)

    def mcc(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (e.g. ``[0.01, 0.5, 0.99]``).
            If None, then the threshold maximizing the metric will be used.
        :returns: The absolute MCC (a value between 0 and 1, 0 being totally dissimilar, 1 being identical).

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.mcc()
        """
        return self.metric("absolute_mcc", thresholds=thresholds)

    def max_per_class_error(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (e.g. ``[0.01, 0.5, 0.99]``).
            If None, then the threshold minimizing the error will be used.
        :returns: Return 1 - min(per class accuracy).

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.max_per_class_error()
        """
        return H2OBinomialModelMetrics._accuracy_to_error(self.metric("min_per_class_accuracy", thresholds=thresholds))

    def mean_per_class_error(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (e.g. ``[0.01, 0.5, 0.99]``).
            If None, then the threshold minimizing the error will be used.
        :returns: mean per class error.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.mean_per_class_error()
        """
        return H2OBinomialModelMetrics._accuracy_to_error(self.metric("mean_per_class_accuracy", thresholds=thresholds))

    @staticmethod
    def _accuracy_to_error(accuracies):
        errors = List()
        errors.extend([acc[0], 1 - acc[1]] for acc in accuracies)
        setattr(errors, 'value',
                [1 - v for v in accuracies.value] if isinstance(accuracies.value, list)
                else 1 - accuracies.value
                )
        return errors

    def metric(self, metric, thresholds=None):
        """
        :param str metric: A metric among :const:`maximizing_metrics`.
        :param thresholds: thresholds parameter must be a list (e.g. ``[0.01, 0.5, 0.99]``).
            If None, then the threshold maximizing the metric will be used.
            If 'all', then all stored thresholds are used and returned with the matching metric.
        :returns: The set of metrics for the list of thresholds.
            The returned list has a 'value' property holding only
            the metric value (if no threshold provided or if provided as a number),
            or all the metric values (if thresholds provided as a list)

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> local_data = [[1, 'a'],[1, 'a'],[1, 'a'],[1, 'a'],[1, 'a'],
        ...               [1, 'a'],[1, 'a'],[1, 'a'],[1, 'a'],[1, 'a'],
        ...               [0, 'b'],[0, 'b'],[0, 'b'],[0, 'b'],[0, 'b'],
        ...               [0, 'b'],[0, 'b'],[0, 'b'],[0, 'b'],[0, 'b']]
        >>> h2o_data = h2o.H2OFrame(local_data)
        >>> h2o_data.set_names(['response', 'predictor'])
        >>> h2o_data["response"] = h2o_data["response"].asfactor()
        >>> gbm = H2OGradientBoostingEstimator(ntrees=1,
        ...                                    distribution="bernoulli")
        >>> gbm.train(x=list(range(1,h2o_data.ncol)),
        ...           y="response",
        ...           training_frame=h2o_data)
        >>> perf = gbm.model_performance()
        >>> perf.metric("tps", [perf.find_threshold_by_max_metric("f1")])[0][1]
        """
        assert_is_type(thresholds, None, 'all', numeric, [numeric])
        if metric not in H2OBinomialModelMetrics.maximizing_metrics:
            raise ValueError("The only allowable metrics are {}".format(', '.join(H2OBinomialModelMetrics.maximizing_metrics)))

        h2o_metric = (H2OBinomialModelMetrics.metrics_aliases[metric] if metric in H2OBinomialModelMetrics.metrics_aliases
                      else metric)
        value_is_scalar = is_type(metric, str) and (thresholds is None or is_type(thresholds, numeric))
        if thresholds is None:
            thresholds = [self.find_threshold_by_max_metric(h2o_metric)]
        elif thresholds == 'all':
            thresholds = None
        elif is_type(thresholds, numeric):
            thresholds = [thresholds]

        metrics = List()
        thresh2d = self._metric_json['thresholds_and_metric_scores']
        if thresholds is None:  # fast path to return all thresholds: skipping find_idx logic
            metrics.extend(list(t) for t in zip(thresh2d['threshold'], thresh2d[h2o_metric]))
        else:
            for t in thresholds:
                idx = self.find_idx_by_threshold(t)
                metrics.append([t, thresh2d[h2o_metric][idx]])

        setattr(metrics, 'value',
                metrics[0][1] if value_is_scalar
                else list(r[1] for r in metrics)
                )
        return metrics

    @deprecated_params({'save_to_file': 'save_plot_path'})
    def plot(self, type="roc", server=False, save_plot_path=None, plot=True):
        """
        Produce the desired metric plot.

        :param type: the type of metric plot. One of (currently supported):

            - ROC curve ('roc')
            - Precision Recall curve ('pr')
            - Gains Lift curve ('gainslift')
            
        :param server: if True, generate plot inline using matplotlib's Anti-Grain Geometry (AGG) backend.
        :param save_plot_path: filename to save the plot to.
        :param plot: ``True`` to plot curve; ``False`` to get a tuple of values at axis x and y of the plot 
                (tprs and fprs for AUC, recall and precision for PR).
        
        :returns: None or values of x and y axis of the plot + the resulting plot (can be accessed using ``result.figure()``).

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.plot(type="roc")
        >>> cars_gbm.plot(type="pr")
        
        """
        assert type in ["roc", "pr", "gains_lift"]
        if type == "roc":
            return self._plot_roc(server, save_plot_path, plot)
        elif type == "pr":
            return self._plot_pr(server, save_plot_path, plot)
        elif type == "gains_lift":
            return self.gains_lift_plot(server=server, save_plot_path=save_plot_path, plot=plot)
    
    def _plot_roc(self, server=False, save_to_file=None, plot=True):
        if plot:
            plt = get_matplotlib_pyplot(server)
            if plt is None:
                return decorate_plot_result(figure=RAISE_ON_FIGURE_ACCESS)
            fig = plt.figure()
            plt.xlabel('False Positive Rate (FPR)')
            plt.ylabel('True Positive Rate (TPR)')
            plt.title('Receiver Operating Characteristic Curve')
            plt.text(0.5, 0.5, r'AUC={0:.4f}'.format(self._metric_json["AUC"]))
            plt.plot(self.fprs, self.tprs, 'b--')
            plt.axis([0, 1, 0, 1])
            plt.grid(True)
            plt.tight_layout()
            if not server: 
                plt.show()
            if save_to_file is not None:  # only save when a figure is actually plotted
                fig.savefig(fname=save_to_file)
            return decorate_plot_result(res=(self.fprs, self.tprs), figure=fig) 
        else:
            return decorate_plot_result(res=(self.fprs, self.tprs))

    def _plot_pr(self, server=False, save_to_file=None, plot=True):
        recalls = [x[0] for x in self.recall(thresholds='all')]
        precisions = self.tprs
        assert len(precisions) == len(recalls), "Precision and recall arrays must have the same length"
        if plot:
            plt = get_matplotlib_pyplot(server)
            if plt is None:
                return decorate_plot_result(figure=RAISE_ON_FIGURE_ACCESS)
            fig = plt.figure()
            plt.xlabel('Recall (TP/(TP+FP))')
            plt.ylabel('Precision (TPR)')
            plt.title('Precision Recall Curve')
            plt.text(0.75, 0.95, r'auc_pr={0:.4f}'.format(self._metric_json["pr_auc"]))
            plt.plot(recalls, precisions, 'b--')
            plt.axis([0, 1, 0, 1])
            plt.grid(True)
            plt.tight_layout()
            if not server: 
                plt.show()
            if save_to_file is not None:  # only save when a figure is actually plotted
                plt.savefig(fname=save_to_file)
            return decorate_plot_result(res=(recalls, precisions), figure=fig)
        else:
            return decorate_plot_result(res=(recalls, precisions))

    @property
    def fprs(self):
        """
        Return all false positive rates for all threshold values.

        :returns: a list of false positive rates.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> r = cars[0].runif()
        >>> train = cars[r > .2]
        >>> valid = cars[r <= .2]
        >>> response_col = "economy_20mpg"
        >>> distribution = "bernoulli"
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3, distribution=distribution, fold_assignment="Random")
        >>> gbm.train(y=response_col, x=predictors, validation_frame=valid, training_frame=train)
        >>> (fprs, tprs) = gbm.roc(train=True, valid=False, xval=False)
        >>> fprs
        """
        return self._metric_json["thresholds_and_metric_scores"]["fpr"]


    @property
    def tprs(self):
        """
        Return all true positive rates for all threshold values.

        :returns: a list of true positive rates.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> r = cars[0].runif()
        >>> train = cars[r > .2]
        >>> valid = cars[r <= .2]
        >>> response_col = "economy_20mpg"
        >>> distribution = "bernoulli"
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3, distribution=distribution, fold_assignment="Random")
        >>> gbm.train(y=response_col, x=predictors, validation_frame=valid, training_frame=train)
        >>> (fprs, tprs) = gbm.roc(train=True, valid=False, xval=False)
        >>> tprs
        """
        return self._metric_json["thresholds_and_metric_scores"]["tpr"]

    def roc(self):
        """
        Return the coordinates of the ROC curve as a tuple containing the false positive rates as a list and true positive rates as a list.
        :returns: The ROC values.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> r = cars[0].runif()
        >>> train = cars[r > .2]
        >>> valid = cars[r <= .2]
        >>> response_col = "economy_20mpg"
        >>> distribution = "bernoulli"
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution=distribution,
        ...                                    fold_assignment="Random")
        >>> gbm.train(x=predictors,
        ...           y=response_col,
        ...           validation_frame=valid,
        ...           training_frame=train)
        >>> gbm.roc(train=True,  valid=False, xval=False)
        """
        return self.fprs, self.tprs

    metrics_aliases = dict(
        fallout='fpr',
        missrate='fnr',
        recall='tpr',
        sensitivity='tpr',
        specificity='tnr'
    )

    #: metrics names allowed for confusion matrix
    maximizing_metrics = ('absolute_mcc', 'accuracy', 'precision',
                          'f0point5', 'f1', 'f2',
                          'mean_per_class_accuracy', 'min_per_class_accuracy',
                          'tns', 'fns', 'fps', 'tps',
                          'tnr', 'fnr', 'fpr', 'tpr') + tuple(metrics_aliases.keys())

    def confusion_matrix(self, metrics=None, thresholds=None):
        """
        Get the confusion matrix for the specified metric.

        :param metrics: A string (or list of strings) among metrics listed in :const:`maximizing_metrics`. Defaults to ``'f1'``.
        :param thresholds: A value (or list of values) between 0 and 1.
            If None, then the thresholds maximizing each provided metric will be used.
        :returns: a list of ConfusionMatrix objects (if there are more than one to return), a single ConfusionMatrix
            (if there is only one), or None if thresholds are metrics scores are missing.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["cylinders"] = cars["cylinders"].asfactor()
        >>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
        >>> response = "cylinders"
        >>> distribution = "multinomial"
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution=distribution)
        >>> gbm.train(x=predictors,
        ...           y = response,
        ...           training_frame = train,
        ...           validation_frame = valid)
        >>> gbm.confusion_matrix(train)
        """
        thresh2d = self._metric_json['thresholds_and_metric_scores']
        if thresh2d is None:
            return None

        # make lists out of metrics and thresholds arguments
        if metrics is None and thresholds is None:
            metrics = ['f1']

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

        if not all(m.lower() in H2OBinomialModelMetrics.maximizing_metrics for m in metrics_list):
            raise ValueError("The only allowable metrics are {}".format(', '.join(H2OBinomialModelMetrics.maximizing_metrics)))

        # make one big list that combines the thresholds and metric-thresholds
        metrics_thresholds = [self.find_threshold_by_max_metric(m) for m in metrics_list]
        for mt in metrics_thresholds:
            thresholds_list.append(mt)
        first_metrics_thresholds_offset = len(thresholds_list) - len(metrics_thresholds)

        actual_thresholds = [float(e[0]) for i, e in enumerate(thresh2d.cell_values)]
        cms = []
        for i, t in enumerate(thresholds_list):
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
                m = metrics_list[i - first_metrics_thresholds_offset]
                table_header = "Confusion Matrix (Act/Pred) for max {} @ threshold = {}".format(m, actual_thresholds[idx])
            else:
                table_header = "Confusion Matrix (Act/Pred) @ threshold = {}".format(actual_thresholds[idx])
            cms.append(ConfusionMatrix(cm=[[c0, fps], [c1, tps]], domains=self._metric_json['domain'],
                                       table_header=table_header))

        if len(cms) == 1:
            return cms[0]
        else:
            return cms

    def find_threshold_by_max_metric(self, metric):
        """
        :param metrics: A string among the metrics listed in :const:`maximizing_metrics`.
        :returns: the threshold at which the given metric is maximal.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> local_data = [[1, 'a'],[1, 'a'],[1, 'a'],[1, 'a'],[1, 'a'],
        ...               [1, 'a'],[1, 'a'],[1, 'a'],[1, 'a'],[1, 'a'],
        ...               [0, 'b'],[0, 'b'],[0, 'b'],[0, 'b'],[0, 'b'],
        ...               [0, 'b'],[0, 'b'],[0, 'b'],[0, 'b'],[0, 'b']]
        >>> h2o_data = h2o.H2OFrame(local_data)
        >>> h2o_data.set_names(['response', 'predictor'])
        >>> h2o_data["response"] = h2o_data["response"].asfactor()
        >>> gbm = H2OGradientBoostingEstimator(ntrees=1,
        ...                                    distribution="bernoulli")
        >>> gbm.train(x=list(range(1,h2o_data.ncol)),
        ...           y="response",
        ...           training_frame=h2o_data)
        >>> perf = gbm.model_performance()
        >>> perf.find_threshold_by_max_metric("f1")
        """
        crit2d = self._metric_json['max_criteria_and_metric_scores']
        # print(crit2d)
        h2o_metric = (H2OBinomialModelMetrics.metrics_aliases[metric] if metric in H2OBinomialModelMetrics.metrics_aliases
                      else metric)
        for e in crit2d.cell_values:
            if e[0] == "max " + h2o_metric.lower():
                return e[1]
        raise ValueError("No metric " + str(metric.lower()))

    def find_idx_by_threshold(self, threshold):
        """
        Retrieve the index in this metric's threshold list at which the given threshold is located.

        :param threshold: Find the index of this input threshold.
        :returns: the index.
        :raises ValueError: if no such index can be found.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> local_data = [[1, 'a'],[1, 'a'],[1, 'a'],[1, 'a'],[1, 'a'],
        ...               [1, 'a'],[1, 'a'],[1, 'a'],[1, 'a'],[1, 'a'],
        ...               [0, 'b'],[0, 'b'],[0, 'b'],[0, 'b'],[0, 'b'],
        ...               [0, 'b'],[0, 'b'],[0, 'b'],[0, 'b'],[0, 'b']]
        >>> h2o_data = h2o.H2OFrame(local_data)
        >>> h2o_data.set_names(['response', 'predictor'])
        >>> h2o_data["response"] = h2o_data["response"].asfactor()
        >>> gbm = H2OGradientBoostingEstimator(ntrees=1,
        ...                                    distribution="bernoulli")
        >>> gbm.train(x=list(range(1,h2o_data.ncol)),
        ...           y="response",
        ...           training_frame=h2o_data)
        >>> perf = gbm.model_performance()
        >>> perf.find_idx_by_threshold(0.45)
        """
        assert_is_type(threshold, numeric)
        thresh2d = self._metric_json['thresholds_and_metric_scores']
        # print(thresh2d)
        for i, e in enumerate(thresh2d.cell_values):
            t = float(e[0])
            if abs(t - threshold) < 1e-8 * max(t, threshold):
                return i
        if 0 <= threshold <= 1:
            thresholds = [float(e[0]) for i, e in enumerate(thresh2d.cell_values)]
            threshold_diffs = [abs(t - threshold) for t in thresholds]
            closest_idx = threshold_diffs.index(min(threshold_diffs))
            closest_threshold = thresholds[closest_idx]
            print("Could not find exact threshold {0}; using closest threshold found {1}."
                  .format(threshold, closest_threshold))
            return closest_idx
        raise ValueError("Threshold must be between 0 and 1, but got {0} ".format(threshold))

    def gains_lift(self):
        """Retrieve the Gains/Lift table.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["cylinders"] = cars["cylinders"].asfactor()
        >>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
        >>> response_col = "cylinders"
        >>> distribution = "multinomial"
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution=distribution)
        >>> gbm.train(x=predictors,
        ...           y = response,
        ...           training_frame = train,
        ...           validation_frame = valid)
        >>> gbm.gains_lift()
        """
        if 'gains_lift_table' in self._metric_json:
            return self._metric_json['gains_lift_table']
        return None

    @deprecated_params({'save_to_file': 'save_plot_path'})
    def gains_lift_plot(self, type="both", server=False, save_plot_path=None, plot=True):
        """
        Plot Gains/Lift curves.

        :param type: one of:

            - "both" (default)
            - "gains"
            - "lift"
            
        :param server: if ``True``, generate plot inline using matplotlib's Anti-Grain Geometry (AGG) backend.
        :param save_plot_path: filename to save the plot to.
        :param plot: ``True`` to plot curve; ``False`` to get a gains lift table.

        :returns: Gains lift table + the resulting plot (can be accessed using ``result.figure()``).
        """
        type = type.lower()
        assert type in ["both", "gains", "lift"]
        gl = self.gains_lift()
        if plot:
            plt = get_matplotlib_pyplot(server)
            if plt is None:
                return decorate_plot_result(figure=RAISE_ON_FIGURE_ACCESS)
            title = []
            ylab = []
            x = gl['cumulative_data_fraction']
            yccr = gl['cumulative_capture_rate']
            ycl = gl['cumulative_lift']
            plt = get_matplotlib_pyplot(server=False, raise_if_not_available=True)
            fig = plt.figure(figsize=(10, 10))
            plt.grid(True)
            if type in ["both", "gains"]:
                plt.plot(x, yccr, zorder=10, label='cumulative capture rate')
                title.append("Gains")
                ylab.append('cumulative capture rate')
            if type in ["both", "lift"]:
                plt.plot(x, ycl, zorder=10, label='cumulative lift')
                title.append("Lift")
                ylab.append('cumulative lift')
            plt.legend(loc=4, fancybox=True, framealpha=0.5)
            plt.xlim(0, None)
            plt.ylim(0, None)
            plt.xlabel('cumulative data fraction')
            plt.ylabel(", ".join(ylab))
            plt.title(" / ".join(title))
            if not server:
                plt.show()
            if save_plot_path is not None:  # only save when a figure is actually plotted
                fig.savefig(fname=save_plot_path)
            return decorate_plot_result(res=gl, figure=fig)
        else:
            return decorate_plot_result(res=gl)

