# -*- encoding: utf-8 -*-
"""
Binomial model.

:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import absolute_import, division, print_function, unicode_literals
from h2o.utils.compatibility import *  # NOQA
from .model_base import ModelBase


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
        for k, v in tm.items():
            m[k] = None if v is None else v.metric("f1", thresholds=thresholds)
        return list(m.values())[0] if len(m) == 1 else m

    def F2(self, thresholds=None, train=False, valid=False, xval=False):
        """Get the F2 for a set of thresholds.
        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
        and "xval"

        Parameters
        ----------
          thresholds : list, optional
            If None, then the thresholds in this set of metrics will be used.

          train : bool, optional
            If True, return the F2 value for the training data.

          valid : bool, optional
            If True, return the F2 value for the validation data.

          xval : bool, optional
            If True, return the F2 value for each of the cross-validated splits.

        Returns
        -------
          The F2 values for the specified key(s).
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in zip(list(tm.keys()), list(tm.values())): m[k] = None if v is None else v.metric("f2",
                                                                                                    thresholds=thresholds)
        return list(m.values())[0] if len(m) == 1 else m

    def F0point5(self, thresholds=None, train=False, valid=False, xval=False):
        """Get the F0.5 for a set of thresholds.
        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
        and "xval"

        Parameters
        ----------
          thresholds : list, optional
            If None, then the thresholds in this set of metrics will be used.

          train : bool, optional
            If True, return the F0point5 value for the training data.

          valid : bool, optional
            If True, return the F0point5 value for the validation data.

          xval : bool, optional
            If True, return the F0point5 value for each of the cross-validated splits.

        Returns
        -------
          The F0point5 values for the specified key(s).
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in zip(list(tm.keys()), list(tm.values())): m[k] = None if v is None else v.metric("f0point5",
                                                                                                    thresholds=thresholds)
        return list(m.values())[0] if len(m) == 1 else m

    def accuracy(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the accuracy for a set of thresholds.
        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
        and "xval"

        Parameters
        ----------
          thresholds : list, optional
            If None, then the thresholds in this set of metrics will be used.

          train : bool, optional
            If True, return the accuracy value for the training data.

          valid : bool, optional
            If True, return the accuracy value for the validation data.

          xval : bool, optional
            If True, return the accuracy value for each of the cross-validated splits.

        Returns
        -------
          The accuracy values for the specified key(s).
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in zip(list(tm.keys()), list(tm.values())): m[k] = None if v is None else v.metric("accuracy",
                                                                                                    thresholds=thresholds)
        return list(m.values())[0] if len(m) == 1 else m

    def error(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the error for a set of thresholds.
        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
        and "xval"

        Parameters
        ----------
          thresholds : list, optional
            If None, then the thresholds in this set of metrics will be used.

          train : bool, optional
            If True, return the error value for the training data.

          valid : bool, optional
            If True, return the error value for the validation data.

          xval : bool, optional
            If True, return the error value for each of the cross-validated splits.

        Returns
        -------
          The error values for the specified key(s).
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in zip(list(tm.keys()), list(tm.values())): m[k] = None if v is None else [[acc[0], 1 - acc[1]] for acc
                                                                                            in v.metric("accuracy",
                                                                                                        thresholds=thresholds)]
        return list(m.values())[0] if len(m) == 1 else m

    def precision(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the precision for a set of thresholds.
        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
        and "xval"

        Parameters
        ----------
          thresholds : list, optional
            If None, then the thresholds in this set of metrics will be used.

          train : bool, optional
            If True, return the precision value for the training data.

          valid : bool, optional
            If True, return the precision value for the validation data.

          xval : bool, optional
            If True, return the precision value for each of the cross-validated splits.

        Returns
        -------
          The precision values for the specified key(s).
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in zip(list(tm.keys()), list(tm.values())): m[k] = None if v is None else v.metric("precision",
                                                                                                    thresholds=thresholds)
        return list(m.values())[0] if len(m) == 1 else m

    def tpr(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the True Positive Rate for a set of thresholds.
        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
        and "xval"

        Parameters
        ----------
          thresholds : list, optional
            If None, then the thresholds in this set of metrics will be used.

          train : bool, optional
            If True, return the tpr value for the training data.

          valid : bool, optional
            If True, return the tpr value for the validation data.

          xval : bool, optional
            If True, return the tpr value for each of the cross-validated splits.

        Returns
        -------
          The tpr values for the specified key(s).
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in zip(list(tm.keys()), list(tm.values())): m[k] = None if v is None else v.metric("tpr",
                                                                                                    thresholds=thresholds)
        return list(m.values())[0] if len(m) == 1 else m

    def tnr(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the True Negative Rate for a set of thresholds.
        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
        and "xval"

        Parameters
        ----------
          thresholds : list, optional
            If None, then the thresholds in this set of metrics will be used.

          train : bool, optional
            If True, return the tnr value for the training data.

          valid : bool, optional
            If True, return the tnr value for the validation data.

          xval : bool, optional
            If True, return the tnr value for each of the cross-validated splits.

        Returns
        -------
          The tnr values for the specified key(s).
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in zip(list(tm.keys()), list(tm.values())): m[k] = None if v is None else v.metric("tnr",
                                                                                                    thresholds=thresholds)
        return list(m.values())[0] if len(m) == 1 else m

    def fnr(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the False Negative Rates for a set of thresholds.
        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
        and "xval"

        Parameters
        ----------
          thresholds : list, optional
            If None, then the thresholds in this set of metrics will be used.

          train : bool, optional
            If True, return the fnr value for the training data.

          valid : bool, optional
            If True, return the fnr value for the validation data.

          xval : bool, optional
            If True, return the fnr value for each of the cross-validated splits.

        Returns
        -------
          The fnr values for the specified key(s).
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in zip(list(tm.keys()), list(tm.values())): m[k] = None if v is None else v.metric("fnr",
                                                                                                    thresholds=thresholds)
        return list(m.values())[0] if len(m) == 1 else m

    def fpr(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the False Positive Rates for a set of thresholds.
        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
        and "xval"

        Parameters
        ----------
          thresholds : list, optional
            If None, then the thresholds in this set of metrics will be used.

          train : bool, optional
            If True, return the fpr value for the training data.

          valid : bool, optional
            If True, return the fpr value for the validation data.

          xval : bool, optional
            If True, return the fpr value for each of the cross-validated splits.

        Returns
        -------
          The fpr values for the specified key(s).
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in zip(list(tm.keys()), list(tm.values())): m[k] = None if v is None else v.metric("fpr",
                                                                                                    thresholds=thresholds)
        return list(m.values())[0] if len(m) == 1 else m

    def recall(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the Recall (AKA True Positive Rate) for a set of thresholds.
        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
        and "xval"

        Parameters
        ----------
          thresholds : list, optional
            If None, then the thresholds in this set of metrics will be used.

          train : bool, optional
            If True, return the recall value for the training data.

          valid : bool, optional
            If True, return the recall value for the validation data.

          xval : bool, optional
            If True, return the recall value for each of the cross-validated splits.

        Returns
        -------
          The recall values for the specified key(s).
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in zip(list(tm.keys()), list(tm.values())): m[k] = None if v is None else v.metric("tpr",
                                                                                                    thresholds=thresholds)
        return list(m.values())[0] if len(m) == 1 else m

    def sensitivity(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the sensitivity (AKA True Positive Rate or Recall) for a set of thresholds.
        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
        and "xval"

        Parameters
        ----------
          thresholds : list, optional
            If None, then the thresholds in this set of metrics will be used.

          train : bool, optional
            If True, return the sensitivity value for the training data.

          valid : bool, optional
            If True, return the sensitivity value for the validation data.

          xval : bool, optional
            If True, return the sensitivity value for each of the cross-validated splits.

        Returns
        -------
          The sensitivity values for the specified key(s).
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in zip(list(tm.keys()), list(tm.values())): m[k] = None if v is None else v.metric("tpr",
                                                                                                    thresholds=thresholds)
        return list(m.values())[0] if len(m) == 1 else m

    def fallout(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the Fallout (AKA False Positive Rate) for a set of thresholds.
        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
        and "xval"

        Parameters
        ----------
          thresholds : list, optional
            If None, then the thresholds in this set of metrics will be used.

          train : bool, optional
            If True, return the fallout value for the training data.

          valid : bool, optional
            If True, return the fallout value for the validation data.

          xval : bool, optional
            If True, return the fallout value for each of the cross-validated splits.

        Returns
        -------
          The fallout values for the specified key(s).
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in zip(list(tm.keys()), list(tm.values())): m[k] = None if v is None else v.metric("fpr",
                                                                                                    thresholds=thresholds)
        return list(m.values())[0] if len(m) == 1 else m

    def missrate(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the miss rate (AKA False Negative Rate) for a set of thresholds.
        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
        and "xval"

        Parameters
        ----------
          thresholds : list, optional
            If None, then the thresholds in this set of metrics will be used.

          train : bool, optional
            If True, return the missrate value for the training data.

          valid : bool, optional
            If True, return the missrate value for the validation data.

          xval : bool, optional
            If True, return the missrate value for each of the cross-validated splits.

        Returns
        -------
          The missrate values for the specified key(s).
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in zip(list(tm.keys()), list(tm.values())): m[k] = None if v is None else v.metric("fnr",
                                                                                                    thresholds=thresholds)
        return list(m.values())[0] if len(m) == 1 else m

    def specificity(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the specificity (AKA True Negative Rate) for a set of thresholds.
        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
        and "xval"

        Parameters
        ----------
          thresholds : list, optional
            If None, then the thresholds in this set of metrics will be used.

          train : bool, optional
            If True, return the specificity value for the training data.

          valid : bool, optional
            If True, return the specificity value for the validation data.

          xval : bool, optional
            If True, return the specificity value for each of the cross-validated splits.

        Returns
        -------
          The specificity values for the specified key(s).
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in zip(list(tm.keys()), list(tm.values())): m[k] = None if v is None else v.metric("tnr",
                                                                                                    thresholds=thresholds)
        return list(m.values())[0] if len(m) == 1 else m

    def mcc(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the mcc for a set of thresholds.
        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
        and "xval"

        Parameters
        ----------
          thresholds : list, optional
            If None, then the thresholds in this set of metrics will be used.

          train : bool, optional
            If True, return the mcc value for the training data.

          valid : bool, optional
            If True, return the mcc value for the validation data.

          xval : bool, optional
            If True, return the mcc value for each of the cross-validated splits.

        Returns
        -------
          The mcc values for the specified key(s).
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in zip(list(tm.keys()), list(tm.values())): m[k] = None if v is None else v.metric("absolute_mcc",
                                                                                                    thresholds=thresholds)
        return list(m.values())[0] if len(m) == 1 else m

    def max_per_class_error(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the max per class error for a set of thresholds.
        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
        and "xval"

        Parameters
        ----------
          thresholds : list, optional
            If None, then the thresholds in this set of metrics will be used.

          train : bool, optional
            If True, return the max_per_class_error value for the training data.

          valid : bool, optional
            If True, return the max_per_class_error value for the validation data.

          xval : bool, optional
            If True, return the max_per_class_error value for each of the cross-validated splits.

        Returns
        -------
          The max_per_class_error values for the specified key(s).
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in zip(list(tm.keys()), list(tm.values())): m[k] = None if v is None else [[mpca[0], 1 - mpca[1]] for
                                                                                            mpca in v.metric(
                "min_per_class_accuracy", thresholds=thresholds)]
        return list(m.values())[0] if len(m) == 1 else m

    def mean_per_class_error(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the mean per class error for a set of thresholds.
        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
        and "xval"

        Parameters
        ----------
          thresholds : list, optional
            If None, then the thresholds in this set of metrics will be used.

          train : bool, optional
            If True, return the mean_per_class_error value for the training data.

          valid : bool, optional
            If True, return the mean_per_class_error value for the validation data.

          xval : bool, optional
            If True, return the mean_per_class_error value for each of the cross-validated splits.

        Returns
        -------
          The mean_per_class_error values for the specified key(s).
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in zip(list(tm.keys()), list(tm.values())): m[k] = None if v is None else [[mpca[0], 1 - mpca[1]] for
                                                                                            mpca in v.metric(
                "mean_per_class_accuracy", thresholds=thresholds)]
        return list(m.values())[0] if len(m) == 1 else m

    def metric(self, metric, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the metric value for a set of thresholds.
        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
        and "xval"

        Parameters
        ----------
          thresholds : list, optional
            If None, then the thresholds in this set of metrics will be used.

          train : bool, optional
            If True, return the metric value for the training data.

          valid : bool, optional
            If True, return the metric value for the validation data.

          xval : bool, optional
            If True, return the metric value for each of the cross-validated splits.

        Returns
        -------
          The metric values for the specified key(s).
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in zip(list(tm.keys()), list(tm.values())): m[k] = None if v is None else v.metric(metric, thresholds)
        return list(m.values())[0] if len(m) == 1 else m

    def plot(self, timestep="AUTO", metric="AUTO", **kwargs):
        """Plots training set (and validation set if available) scoring history for an
        H2OBinomialModel. The timestep and metric arguments are restricted to what is
        available in its scoring history.

        Parameters
        ----------
          timestep : str
             A unit of measurement for the x-axis.

          metric : str
            A unit of measurement for the y-axis.
        """

        if self._model_json["algo"] in ("deeplearning", "drf", "gbm"):
            if metric == "AUTO":
                metric = "logloss"
            elif metric not in ("logloss", "auc", "classification_error", "rmse"):
                raise ValueError(
                    "metric for H2OBinomialModel must be one of: AUTO, logloss, auc, classification_error, rmse")

        self._plot(timestep=timestep, metric=metric, **kwargs)

    def roc(self, train=False, valid=False, xval=False):
        """Return the coordinates of the ROC curve for a given set of data, as a two-tuple
        containing the false positive rates as a list and true positive rates as a list.
        If all are False (default), then return is the training data. If more than one ROC
        curve is requested, the data is returned as a dictionary of two-tuples.

        Parameters
        ----------
          train : bool, optional
            If True, return the roc value for the training data.

          valid : bool, optional
            If True, return the roc value for the validation data.

          xval : bool, optional
            If True, return the roc value for each of the cross-validated splits.

        Returns
        -------
          The roc values for the specified key(s).
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in zip(list(tm.keys()), list(tm.values())):
            if v is not None:
                m[k] = (v.fprs, v.tprs)
        return list(m.values())[0] if len(m) == 1 else m

    def gains_lift(self, train=False, valid=False, xval=False):
        """
        Get the Gains/Lift table for the specified metrics
        If all are False (default), then return the training metric Gains/Lift table.
        If more than one options is set to True, then return a dictionary of metrics where t
        he keys are "train", "valid", and "xval"

        Parameters
        ----------
          train : bool, optional
            If True, return the gains_lift value for the training data.

          valid : bool, optional
            If True, return the gains_lift value for the validation data.

          xval : bool, optional
            If True, return the gains_lift value for each of the cross-validated splits.

        Returns
        -------
          The gains_lift values for the specified key(s).
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in zip(list(tm.keys()), list(tm.values())): m[k] = None if v is None else v.gains_lift()
        return list(m.values())[0] if len(m) == 1 else m

    def confusion_matrix(self, metrics=None, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the confusion matrix for the specified metrics/thresholds
        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the
        keys are "train", "valid", and "xval"

        Parameters
        ----------
          metrics : str, list
            One or more of "min_per_class_accuracy", "absolute_mcc", "tnr", "fnr", "fpr",
            "tpr", "precision", "accuracy", "f0point5", "f2", "f1".

          thresholds : list, optional
            If None, then the thresholds in this set of metrics will be used.

          train : bool, optional
            If True, return the confusion_matrix for the training data.

          valid : bool, optional
            If True, return the confusion_matrix for the validation data.

          xval : bool, optional
            If True, return the confusion_matrix for each of the cross-validated splits.

        Returns
        -------
          The metric values for the specified key(s).
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in zip(list(tm.keys()), list(tm.values())): m[k] = None if v is None else v.confusion_matrix(
            metrics=metrics, thresholds=thresholds)
        return list(m.values())[0] if len(m) == 1 else m

    def find_threshold_by_max_metric(self, metric, train=False, valid=False, xval=False):
        """If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
        and "xval"

        Parameters
        ----------
          train : bool, optional
            If True, return the find_threshold_by_max_metric value for the training data.

          valid : bool, optional
            If True, return the find_threshold_by_max_metric value for the validation data.

          xval : bool, optional
            If True, return the find_threshold_by_max_metric value for each of the cross-validated splits.

        Returns
        -------
          The find_threshold_by_max_metric values for the specified key(s).
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in zip(list(tm.keys()), list(tm.values())): m[
            k] = None if v is None else v.find_threshold_by_max_metric(metric)
        return list(m.values())[0] if len(m) == 1 else m

    def find_idx_by_threshold(self, threshold, train=False, valid=False, xval=False):
        """
        Retrieve the index in this metric's threshold list at which the given threshold is located.
        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
        and "xval"

        Parameters
        ----------
          train : bool, optional
            If True, return the find_idx_by_threshold value for the training data.

          valid : bool, optional
            If True, return the find_idx_by_threshold value for the validation data.

          xval : bool, optional
            If True, return the find_idx_by_threshold value for each of the cross-validated splits.

        Returns
        -------
          The find_idx_by_threshold values for the specified key(s).
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in zip(list(tm.keys()), list(tm.values())): m[k] = None if v is None else v.find_idx_by_threshold(
            threshold)
        return list(m.values())[0] if len(m) == 1 else m
