# -*- encoding: utf-8 -*-
from __future__ import absolute_import, division, print_function, unicode_literals

import h2o
from h2o.exceptions import H2OValueError
from h2o.frame import H2OFrame
from h2o.model import ModelBase
from h2o.model.extensions import has_extension
from h2o.utils.compatibility import *  # NOQA
from h2o.utils.typechecks import assert_is_type


class H2OMultinomialModel(ModelBase):

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

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param train: If train is ``True``, then return the hit ratio value for the training data.
        :param valid: If valid is ``True``, then return the hit ratio value for the validation data.
        :param xval:  If xval is ``True``, then return the hit ratio value for the cross validation data.
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
        Retrieve the mean per class error across all classes.

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param bool train: If ``True``, return the ``mean_per_class_error`` value for the training data.
        :param bool valid: If ``True``, return the ``mean_per_class_error`` value for the validation data.
        :param bool xval:  If ``True``, return the ``mean_per_class_error`` value for each of the cross-validated splits.

        :returns: The ``mean_per_class_error`` values for the specified key(s).

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

    def multinomial_auc_table(self, train=False, valid=False, xval=False):
        """
        Retrieve the multinomial AUC table.
    
        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".
    
        :param bool train: If ``True``, return the ``multinomial_auc_table`` for the training data.
        :param bool valid: If ``True``, return the ``multinomial_auc_table`` for the validation data.
        :param bool xval:  If ``True``, return the ``multinomial_auc_table`` for each of the cross-validated splits.
    
        :returns: The ``multinomial_auc_table`` values for the specified key(s).
    
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
        >>> multinomial_auc_table = gbm.multinomial_auc_table() # <- Default: return training metric
        >>> multinomial_auc_table
        >>> multinomial_auc_table1 = gbm.multinomial_auc_table(train=True,
        ...                                        valid=True,
        ...                                        xval=True)
        >>> multinomial_auc_table1
            """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in zip(list(tm.keys()), list(tm.values())): m[k] = None if v is None else v.multinomial_auc_table()
        return list(m.values())[0] if len(m) == 1 else m

    def multinomial_aucpr_table(self, train=False, valid=False, xval=False):
        """
        Retrieve the multinomial PR AUC table.
    
        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".
    
        :param bool train: If ``True``, return the ``multinomial_aucpr_table`` for the training data.
        :param bool valid: If ``True``, return the ``multinomial_aucpr_table`` for the validation data.
        :param bool xval:  If ``True``, return the ``multinomial_aucpr_table`` for each of the cross-validated splits.
    
        :returns: The ``average_pairwise_auc`` values for the specified key(s).
    
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
        >>> multinomial_aucpr_table = gbm.multinomial_aucpr_table() # <- Default: return training metric
        >>> multinomial_aucpr_table
        >>> multinomial_aucpr_table1 = gbm.multinomial_aucpr_table(train=True,
        ...                                        valid=True,
        ...                                        xval=True)
        >>> multinomial_aucpr_table1
            """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in zip(list(tm.keys()), list(tm.values())): m[k] = None if v is None else v.multinomial_aucpr_table()
        return list(m.values())[0] if len(m) == 1 else m

    def plot(self, timestep="AUTO", metric="AUTO", save_plot_path=None, **kwargs):
        """
        Plots training set (and validation set if available) scoring history for an H2OMultinomialModel. The timestep
        and metric arguments are restricted to what is available in its scoring history.

        :param timestep: A unit of measurement for the x-axis. One of:

            - 'AUTO'
            - 'duration'
            - 'number_of_trees'
            
        :param metric: A unit of measurement for the y-axis. One of:

            - 'AUTO'
            - 'logloss'
            - 'classification_error'
            - 'rmse'

        :returns: Object that contains the resulting scoring history plot (can be accessed using ``result.figure()``).

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
        if not has_extension(self, 'ScoringHistory'):
            raise H2OValueError("Scoring history plot is not available for this type of model (%s)." % self.algo)

        valid_metrics = self._allowed_metrics('multinomial')
        if valid_metrics is not None:
            assert_is_type(metric, 'AUTO', *valid_metrics), "metric for H2OMultinomialModel must be one of %s" % valid_metrics
        if metric == "AUTO":
            metric = self._default_metric('multinomial') or 'AUTO'
        return self.scoring_history_plot(timestep=timestep, metric=metric, save_plot_path=save_plot_path, **kwargs)
