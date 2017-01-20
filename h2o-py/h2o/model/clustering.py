# -*- encoding: utf-8 -*-
from __future__ import absolute_import, division, print_function, unicode_literals
from h2o.utils.compatibility import *  # NOQA
from .model_base import ModelBase


class H2OClusteringModel(ModelBase):

    def size(self, train=False, valid=False, xval=False):
        """
        Get the sizes of each cluster.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where
        the keys are "train", "valid", and "xval".

        :param bool train: If True, return the cluster sizes for the training data.
        :param bool valid: If True, return the cluster sizes for the validation data.
        :param bool xval: If True, return the cluster sizes for each of the cross-validated splits.

        :returns: The cluster sizes for the specified key(s).
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in tm.items():
            m[k] = None if v is None else [v[2] for v in v._metric_json["centroid_stats"].cell_values]
        return list(m.values())[0] if len(m) == 1 else m


    def num_iterations(self):
        """Get the number of iterations it took to converge or reach max iterations."""
        o = self._model_json["output"]
        return o["model_summary"]["number_of_iterations"][0]


    def betweenss(self, train=False, valid=False, xval=False):
        """
        Get the between cluster sum of squares.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where
        the keys are "train", "valid", and "xval".

        :param bool train: If True, return the between cluster sum of squares value for the training data.
        :param bool valid: If True, return the between cluster sum of squares value for the validation data.
        :param bool xval: If True, return the between cluster sum of squares value for each of the
            cross-validated splits.

        :returns: The between cluster sum of squares values for the specified key(s).
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in tm.items():
            m[k] = None if v is None else v._metric_json["betweenss"]
        return list(m.values())[0] if len(m) == 1 else m


    def totss(self, train=False, valid=False, xval=False):
        """
        Get the total sum of squares.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where
        the keys are "train", "valid", and "xval".

        :param bool train: If True, return the total sum of squares value for the training data.
        :param bool valid: If True, return the total sum of squares value for the validation data.
        :param bool xval: If True, return the total sum of squares value for each of the cross-validated splits.

        :returns: The total sum of squares values for the specified key(s).
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in tm.items():
            m[k] = None if v is None else v._metric_json["totss"]
        return list(m.values())[0] if len(m) == 1 else m


    def tot_withinss(self, train=False, valid=False, xval=False):
        """
        Get the total within cluster sum of squares.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where
        the keys are "train", "valid", and "xval".

        :param bool train: If True, return the total within cluster sum of squares value for the training data.
        :param bool valid: If True, return the total within cluster sum of squares value for the validation data.
        :param bool xval: If True, return the total within cluster sum of squares value for each of the
            cross-validated splits.

        :returns: The total within cluster sum of squares values for the specified key(s).
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in tm.items():
            m[k] = None if v is None else v._metric_json["tot_withinss"]
        return list(m.values())[0] if len(m) == 1 else m


    def withinss(self, train=False, valid=False, xval=False):
        """
        Get the within cluster sum of squares for each cluster.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where
        the keys are "train", "valid", and "xval".

        :param bool train: If True, return the total sum of squares value for the training data.
        :param bool valid: If True, return the total sum of squares value for the validation data.
        :param bool xval: If True, return the total sum of squares value for each of the cross-validated splits.

        :returns: The total sum of squares values for the specified key(s).
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in tm.items():
            m[k] = None if v is None else [z[-1] for z in v._metric_json["centroid_stats"].cell_values]
        return list(m.values())[0] if len(m) == 1 else m


    def centroid_stats(self, train=False, valid=False, xval=False):
        """
        Get the centroid statistics for each cluster.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where
        the keys are "train", "valid", and "xval".

        :param bool train: If True, return the centroid statistic for the training data.
        :param bool valid: If True, return the centroid statistic for the validation data.
        :param bool xval: If True, return the centroid statistic for each of the cross-validated splits.

        :returns: The centroid statistics for the specified key(s).
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in tm.items():
            m[k] = None if v is None else v._metric_json["centroid_stats"]
        return list(m.values())[0] if len(m) == 1 else m


    def centers(self):
        """The centers for the KMeans model."""
        o = self._model_json["output"]
        cvals = o["centers"].cell_values
        centers = [list(cval[1:]) for cval in cvals]
        return centers


    def centers_std(self):
        """The standardized centers for the kmeans model."""
        o = self._model_json["output"]
        cvals = o["centers_std"].cell_values
        centers_std = [list(cval[1:]) for cval in cvals]
        centers_std = [list(x) for x in zip(*centers_std)]
        return centers_std
