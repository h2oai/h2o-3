# -*- encoding: utf-8 -*-
from __future__ import absolute_import, division, print_function, unicode_literals

from h2o.exceptions import H2OValueError
from h2o.model import ModelBase
from h2o.model.extensions import has_extension
# noinspection PyUnresolvedReferences
from h2o.utils.compatibility import *  # NOQA
from h2o.utils.shared_utils import _colmean
from h2o.utils.typechecks import assert_is_type


class H2ORegressionModel(ModelBase):

    def plot(self, timestep="AUTO", metric="AUTO", save_plot_path=None, **kwargs):
        """
        Plots training set (and validation set if available) scoring history for an H2ORegressionModel. The ``timestep``
        and ``metric`` arguments are restricted to what is available in its scoring history.

        :param timestep: A unit of measurement for the x-axis.
        :param metric: A unit of measurement for the y-axis.
        :param save_plot_path: a path to save the plot via using matplotlib function savefig.

        :returns: Object that contains the resulting scoring history plot (can be accessed using ``result.figure()``).

        :examples:

        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> r = cars[0].runif()
        >>> train = cars[r > .2]
        >>> valid = cars[r <= .2]
        >>> response_col = "economy"
        >>> distribution = "gaussian"
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution=distribution,
        ...                                    fold_assignment="Random")
        >>> gbm.train(x=predictors,
        ...           y=response_col,
        ...           training_frame=train,
        ...           validation_frame=valid)
        >>> gbm.plot(timestep="AUTO", metric="AUTO",)
        """
        if not has_extension(self, 'ScoringHistory'):
            raise H2OValueError("Scoring history plot is not available for this type of model (%s)." % self.algo)

        valid_metrics = self._allowed_metrics('regression')
        if valid_metrics is not None:
            assert_is_type(metric, 'AUTO', *valid_metrics), "metric for H2ORegressionModel must be one of %s" % valid_metrics
        if metric == "AUTO":
            metric = self._default_metric('regression') or 'AUTO'
        self.scoring_history_plot(timestep=timestep, metric=metric, save_plot_path=save_plot_path, **kwargs)


def _mean_var(frame, weights=None):
    """
    Compute the (weighted) mean and variance.

    :param frame: Single column H2OFrame.
    :param weights: optional weights column.
    :returns: The (weighted) mean and variance.
    """
    return _colmean(frame), frame.var()


def h2o_mean_absolute_error(y_actual, y_predicted, weights=None):
    """
    Mean absolute error regression loss.

    :param y_actual: H2OFrame of actual response.
    :param y_predicted: H2OFrame of predicted response.
    :param weights: (Optional) sample weights.
    :returns: mean absolute error loss (best is 0.0).
    """
    ModelBase._check_targets(y_actual, y_predicted)
    return _colmean((y_predicted - y_actual).abs())


def h2o_mean_squared_error(y_actual, y_predicted, weights=None):
    """
    Mean squared error regression loss

    :param y_actual: H2OFrame of actual response.
    :param y_predicted: H2OFrame of predicted response.
    :param weights: (Optional) sample weights.
    :returns: mean squared error loss (best is 0.0).
    """
    ModelBase._check_targets(y_actual, y_predicted)
    return _colmean((y_predicted - y_actual) ** 2)


def h2o_median_absolute_error(y_actual, y_predicted):
    """
    Median absolute error regression loss

    :param y_actual: H2OFrame of actual response.
    :param y_predicted: H2OFrame of predicted response.
    :returns: median absolute error loss (best is 0.0).
    """
    ModelBase._check_targets(y_actual, y_predicted)
    return (y_predicted - y_actual).abs().median()


def h2o_explained_variance_score(y_actual, y_predicted, weights=None):
    """
    Explained variance regression score function.

    :param y_actual: H2OFrame of actual response.
    :param y_predicted: H2OFrame of predicted response.
    :param weights: (Optional) sample weights.
    :returns: the explained variance score.
    """
    ModelBase._check_targets(y_actual, y_predicted)

    _, numerator = _mean_var(y_actual - y_predicted, weights)
    _, denominator = _mean_var(y_actual, weights)
    if denominator == 0.0:
        return 1. if numerator == 0 else 0.  # 0/0 => 1, otherwise, 0
    return 1 - numerator / denominator


def h2o_r2_score(y_actual, y_predicted, weights=1.):
    """
    R-squared (coefficient of determination) regression score function

    :param y_actual: H2OFrame of actual response.
    :param y_predicted: H2OFrame of predicted response.
    :param weights: (Optional) sample weights.
    :returns: R-squared (best is 1.0, lower is worse).
    """
    ModelBase._check_targets(y_actual, y_predicted)
    numerator = (weights * (y_actual - y_predicted) ** 2).sum().flatten()
    denominator = (weights * (y_actual - _colmean(y_actual)) ** 2).sum().flatten()

    if denominator == 0.0:
        return 1. if numerator == 0. else 0.  # 0/0 => 1, else 0
    return 1 - numerator / denominator
