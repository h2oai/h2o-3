# -*- encoding: utf-8 -*-
import functools as ft

import h2o
from h2o.utils.typechecks import assert_is_type
from ._base import _fetch_state, _fetch_leaderboard
from ._estimator import H2OAutoML
from ._output import H2OAutoMLOutput

__all__ = ['H2OAutoML', 'get_automl', 'get_leaderboard']


def get_automl(project_name):
    """
    Retrieve information about an AutoML instance.

    :param str project_name:  A string indicating the project_name of the automl instance to retrieve.
    :returns: A dictionary containing the project_name, leader model, leaderboard, event_log.
    """
    state = _fetch_state(project_name)
    return H2OAutoMLOutput(state)


def get_leaderboard(aml, extra_columns=None):
    """
    Retrieve the leaderboard from the AutoML instance.
    Contrary to the default leaderboard attached to the automl instance, this one can return columns other than the metrics.
    
    :param H2OAutoML aml: the instance for which to return the leaderboard.
    :param extra_columns: a string or a list of string specifying which optional columns should be added to the leaderboard. Defaults to None.
        Currently supported extensions are:
        - 'ALL': adds all columns below.
        - 'training_time_ms': column providing the training time of each model in milliseconds (doesn't include the training of cross validation models).
        - 'predict_time_per_row_ms`: column providing the average prediction time by the model for a single row.
        - 'algo': column providing the algorithm name for each model.
    :return: An H2OFrame representing the leaderboard.
    
    :examples:
    
    >>> aml = H2OAutoML(max_runtime_secs=30)
    >>> aml.train(y=y, training_frame=train)
    >>> lb_all = h2o.automl.get_leaderboard(aml, 'ALL')
    >>> lb_custom = h2o.automl.get_leaderboard(aml, ['predict_time_per_row_ms', 'training_time_ms'])
    >>> lb_custom_sorted = lb_custom.sort(by='predict_time_per_row_ms')
    """
    assert_is_type(aml, H2OAutoML, H2OAutoMLOutput)
    return _fetch_leaderboard(aml.key, extra_columns)
