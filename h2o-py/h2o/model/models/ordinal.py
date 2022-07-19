# -*- encoding: utf-8 -*-
from __future__ import absolute_import, division, print_function, unicode_literals

import h2o
from h2o.exceptions import H2OValueError
from h2o.frame import H2OFrame
from h2o.model import ModelBase
from h2o.model.extensions import has_extension
from h2o.utils.compatibility import *  # NOQA
from h2o.utils.typechecks import assert_is_type


class H2OOrdinalModel(ModelBase):

    def confusion_matrix(self, data):
        """
        Returns a confusion matrix based on H2O's default prediction threshold for a dataset.

        :param H2OFrame data: the frame with the prediction results for which the confusion matrix should be extracted.
        """
        assert_is_type(data, H2OFrame)
        j = h2o.api("POST /3/Predictions/models/%s/frames/%s" % (self._id, data.frame_id))
        return j["model_metrics"][0]["cm"]["table"]

    def hit_ratio_table(self, train=False, valid=False, xval=False):
        """
        Retrieve the Hit Ratios.

        If all are ``False`` (default), then return the training metric value.
        If more than one options is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param train: If train is ``True``, then return the hit ratio value for the training data.
        :param valid: If valid is ``True``, then return the hit ratio value for the validation data.
        :param xval:  If xval is ``True``, then return the hit ratio value for the cross validation data.
        :return: The hit ratio for this regression model.
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in zip(list(tm.keys()), list(tm.values())): m[k] = None if v is None else v.hit_ratio_table()
        return list(m.values())[0] if len(m) == 1 else m

    def mean_per_class_error(self, train=False, valid=False, xval=False):
        """
        Retrieve the mean per class error across all classes

        If all are ``False`` (default), then return the training metric value.
        If more than one options is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param bool train: If ``True``, return the ``mean_per_class_error`` value for the training data.
        :param bool valid: If ``True``, return the ``mean_per_class_error`` value for the validation data.
        :param bool xval:  If ``True``, return the ``mean_per_class_error`` value for each of the cross-validated splits.

        :returns: The ``mean_per_class_error`` values for the specified key(s).
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in zip(list(tm.keys()), list(tm.values())): m[k] = None if v is None else v.mean_per_class_error()
        return list(m.values())[0] if len(m) == 1 else m

    def plot(self, timestep="AUTO", metric="AUTO", save_plot_path=None, **kwargs):
        """
        Plots training set (and validation set if available) scoring history for an H2OOrdinalModel. The ``timestep``
        and ``metric`` arguments are restricted to what is available in its scoring history.

        :param timestep: A unit of measurement for the x-axis.
        :param metric: A unit of measurement for the y-axis.
        :param save_plot_path: a path to save the plot via using matplotlib function savefig.

        :returns: Object that contains the resulting scoring history plot (can be accessed using ``result.figure()``).
        """
        if not has_extension(self, 'ScoringHistory'):
            raise H2OValueError("Scoring history plot is not available for this type of model (%s)." % self.algo)

        self.scoring_history_plot(timestep=timestep, metric=metric, save_plot_path=save_plot_path, **kwargs)
