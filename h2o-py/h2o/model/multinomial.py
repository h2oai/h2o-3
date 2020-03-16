# -*- encoding: utf-8 -*-
from __future__ import absolute_import, division, print_function, unicode_literals
from h2o.utils.compatibility import *  # NOQA

from ..frame import H2OFrame
import h2o
from .model_base import ModelBase
from h2o.utils.typechecks import assert_is_type



class H2OMultinomialModel(ModelBase):

    def _make_model(self):
        return H2OMultinomialModel()


    def confusion_matrix(self, data):
        """
        Returns a confusion matrix based of H2O's default prediction threshold for a dataset.

        :param H2OFrame data: the frame with the prediction results for which the confusion matrix should be extracted.

        :examples:

        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["cylinders"] = cars["cylinders"].asfactor()
        >>> r = cars[0].runif()
        >>> train = cars[r > .2]
        >>> valid = cars[r <= .2]
        >>> response_col = "cylinders"
        >>> distribution = "multinomial"
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution=distribution)
        >>> gbm.train(x=predictors,
        ...           y=response_col,
        ...           training_frame=train,
        ...           validation_frame=valid)
        >>> confusion_matrix = gbm.confusion_matrix(train)
        >>> confusion_matrix
        """
        assert_is_type(data, H2OFrame)
        j = h2o.api("POST /3/Predictions/models/%s/frames/%s" % (self._id, data.frame_id))
        return j["model_metrics"][0]["cm"]["table"]


    def hit_ratio_table(self, train=False, valid=False, xval=False):
        """
        Retrieve the Hit Ratios.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param train: If train is True, then return the hit ratio value for the training data.
        :param valid: If valid is True, then return the hit ratio value for the validation data.
        :param xval:  If xval is True, then return the hit ratio value for the cross validation data.
        :return: The hit ratio for this regression model.

        :example:

        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["cylinders"] = cars["cylinders"].asfactor()
        >>> r = cars[0].runif()
        >>> train = cars[r > .2]
        >>> valid = cars[r <= .2]
        >>> response_col = "cylinders"
        >>> distribution = "multinomial"
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution=distribution)
        >>> gbm.train(x=predictors,
        ...           y=response_col,
        ...           training_frame=train,
        ...           validation_frame=valid)
        >>> hit_ratio_table = gbm.hit_ratio_table() # <- Default: return training metrics
        >>> hit_ratio_table
        >>> hit_ratio_table1 = gbm.hit_ratio_table(train=True,
        ...                                        valid=True,
        ...                                        xval=True)
        >>> hit_ratio_table1
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in zip(list(tm.keys()), list(tm.values())): m[k] = None if v is None else v.hit_ratio_table()
        return list(m.values())[0] if len(m) == 1 else m


    def mean_per_class_error(self, train=False, valid=False, xval=False):
        """
        Retrieve the mean per class error across all classes

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param bool train: If True, return the mean_per_class_error value for the training data.
        :param bool valid: If True, return the mean_per_class_error value for the validation data.
        :param bool xval:  If True, return the mean_per_class_error value for each of the cross-validated splits.

        :returns: The mean_per_class_error values for the specified key(s).

        :examples:

        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["cylinders"] = cars["cylinders"].asfactor()
        >>> r = cars[0].runif()
        >>> train = cars[r > .2]
        >>> valid = cars[r <= .2]
        >>> response_col = "cylinders"
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> distribution = "multinomial"
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3, distribution=distribution)
        >>> gbm.train(x=predictors,
        ...           y=response_col,
        ...           training_frame=train,
        ...           validation_frame=valid)
        >>> mean_per_class_error = gbm.mean_per_class_error() # <- Default: return training metric
        >>> mean_per_class_error
        >>> mean_per_class_error1 = gbm.mean_per_class_error(train=True,
        ...                                                  valid=True,
        ...                                                  xval=True)
        >>> mean_per_class_error1
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in zip(list(tm.keys()), list(tm.values())): m[k] = None if v is None else v.mean_per_class_error()
        return list(m.values())[0] if len(m) == 1 else m


    def plot(self, timestep="AUTO", metric="AUTO", **kwargs):
        """
        Plots training set (and validation set if available) scoring history for an H2OMultinomialModel. The timestep
        and metric arguments are restricted to what is available in its scoring history.

        :param timestep: A unit of measurement for the x-axis. This can be AUTO, duration, or number_of_trees.
        :param metric: A unit of measurement for the y-axis. This can be AUTO, logloss, classification_error, or rmse.

        :returns: A scoring history plot.

        :examples:

        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["cylinders"] = cars["cylinders"].asfactor()
        >>> r = cars[0].runif()
        >>> train = cars[r > .2]
        >>> valid = cars[r <= .2]
        >>> response_col = "cylinders"
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> distribution = "multinomial"
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution=distribution)
        >>> gbm.train(x=predictors,
        ...           y=response_col,
        ...           training_frame=train,
        ...           validation_frame=valid)
        >>> gbm.plot(metric="AUTO", timestep="AUTO")
        """

        if self._model_json["algo"] in ("deeplearning", "xgboost", "drf", "gbm"):
            if metric == "AUTO":
                metric = "classification_error"
            elif metric not in ("logloss", "classification_error", "rmse"):
                raise ValueError(
                    "metric for H2OMultinomialModel must be one of: AUTO, logloss, classification_error, rmse")

        self._plot(timestep=timestep, metric=metric, **kwargs)
