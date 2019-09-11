# -*- encoding: utf-8 -*-
from __future__ import absolute_import, division, print_function, unicode_literals
from h2o.utils.compatibility import *  # NOQA
from h2o.utils.compatibility import viewitems
from h2o.utils.typechecks import assert_is_type
from .model_base import ModelBase


class H2OBinomialModel(ModelBase):

    def F1(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the F1 value for a set of thresholds.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where
        the keys are "train", "valid", and "xval".

        :param thresholds: If None, then the thresholds in this set of metrics will be used.
        :param bool train: If True, return the F1 value for the training data.
        :param bool valid: If True, return the F1 value for the validation data.
        :param bool xval: If True, return the F1 value for each of the cross-validated splits.

        :returns: The F1 values for the specified key(s).

        :examples:

        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> r = cars[0].runif()
        >>> train = cars[r > .2]
        >>> valid = cars[r <=.2]
        >>> response_col = "economy_20mpg"
        >>> distribution = "bernoulli"
        >>> predictors = ["displacement", "power", "weight", "acceleration", "year"]
        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution=distribution,
        ...                                    fold_assignment="Random")
        >>> gbm.train(y=response_col,
        ...           x=predictors,
        ...           validation_frame=valid,
        ...           training_frame=train)
        >>> gbm.F1(train=False, valid=False, xval=False)# <- Default: return training metric value
        >>> gbm.F1(train=True,  valid=True,  xval=True)
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm):
            m[k] = None if v is None else v.metric("f1", thresholds=thresholds)
        return list(m.values())[0] if len(m) == 1 else m


    def F2(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the F2 for a set of thresholds.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: If None, then the thresholds in this set of metrics will be used.
        :param bool train: If True, return the F2 value for the training data.
        :param bool valid: If True, return the F2 value for the validation data.
        :param bool xval: If True, return the F2 value for each of the cross-validated splits.

        :returns: The F2 values for the specified key(s).

        :examples:

        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> r = cars[0].runif()
        >>> train = cars[r > .2]
        >>> valid = cars[r <=.2]
        >>> response_col = "economy_20mpg"
        >>> distribution = "bernoulli"
        >>> predictors = ["displacement", "power", "weight", "acceleration", "year"]
        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution=distribution,
        ...                                    fold_assignment="Random")
        >>> gbm.train(y=response_col,
        ...           x=predictors,
        ...           validation_frame=valid,
        ...           training_frame=train)
        >>> gbm.F2(train=False, valid=False, xval=False) # <- Default: return training metric value
        >>> gbm.F2(train=True, valid=True, xval=True)
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm):
            m[k] = None if v is None else v.metric("f2", thresholds=thresholds)
        return list(m.values())[0] if len(m) == 1 else m


    def F0point5(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the F0.5 for a set of thresholds.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: If None, then the thresholds in this set of metrics will be used.
        :param bool train: If True, return the F0.5 value for the training data.
        :param bool valid: If True, return the F0.5 value for the validation data.
        :param bool xval: If True, return the F0.5 value for each of the cross-validated splits.

        :returns: The F0.5 values for the specified key(s).

        :examples:

        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> r = cars[0].runif()
        >>> train = cars[r > .2]
        >>> valid = cars[r <=.2]
        >>> response_col = "economy_20mpg"
        >>> distribution = "bernoulli"
        >>> predictors = ["displacement", "power", "weight", "acceleration", "year"]
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution=distribution,
        ...                                    fold_assignment="Random")
        >>> gbm.train(y=response_col,
        ...           x=predictors,
        ...           validation_frame=valid,
        ...           training_frame=train)         
        >>> F0point5 = gbm.F0point5(train=False, valid=False, xval=False) # <- Default: return training metric value
        >>> F0point5 = gbm.F0point5(train=True,  valid=True,  xval=True)
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm):
            m[k] = None if v is None else v.metric("f0point5", thresholds=thresholds)
        return list(m.values())[0] if len(m) == 1 else m


    def accuracy(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the accuracy for a set of thresholds.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: If None, then the thresholds in this set of metrics will be used.
        :param bool train: If True, return the accuracy value for the training data.
        :param bool valid: If True, return the accuracy value for the validation data.
        :param bool xval: If True, return the accuracy value for each of the cross-validated splits.

        :returns: The accuracy values for the specified key(s).

        :examples:

        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> r = cars[0].runif()
        >>> train = cars[r > .2]
        >>> valid = cars[r <=.2]
        >>> response_col = "economy_20mpg"
        >>> distribution = "bernoulli"
        >>> predictors = ["displacement", "power", "weight", "acceleration", "year"]
        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution=distribution,
        ...                                    fold_assignment="Random")
        >>> gbm.train(y=response_col,
        ...           x=predictors,
        ...           validation_frame=valid,
        ...           training_frame=train)
        >>> gbm.accuracy(train=False, valid=False, xval=False) # <- Default: return training metric value
        >>> gbm.accuracy(train=True, valid=True, xval=True)
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm):
            m[k] = None if v is None else v.metric("accuracy", thresholds=thresholds)
        return list(m.values())[0] if len(m) == 1 else m


    def error(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the error for a set of thresholds.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: If None, then the thresholds in this set of metrics will be used.
        :param bool train: If True, return the error value for the training data.
        :param bool valid: If True, return the error value for the validation data.
        :param bool xval: If True, return the error value for each of the cross-validated splits.

        :returns: The error values for the specified key(s).

        :examples:

        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> r = cars[0].runif()
        >>> train = cars[r > .2]
        >>> valid = cars[r <=.2]
        >>> response_col = "economy_20mpg"
        >>> distribution = "bernoulli"
        >>> predictors = ["displacement", "power", "weight", "acceleration", "year"]
        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution=distribution,
        ...                                    fold_assignment="Random")
        >>> gbm.train(y=response_col,
        ...           x=predictors,
        ...           validation_frame=valid,
        ...           training_frame=train)
        >>> gbm.error(train=False, valid=False, xval=False) # <- Default: return training metric
        >>> gbm.error(train=True, valid=True, xval=True)
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm):
            m[k] = None if v is None else [[acc[0], 1 - acc[1]] for acc in v.metric("accuracy", thresholds=thresholds)]
        return list(m.values())[0] if len(m) == 1 else m


    def precision(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the precision for a set of thresholds.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: If None, then the thresholds in this set of metrics will be used.
        :param bool train: If True, return the precision value for the training data.
        :param bool valid: If True, return the precision value for the validation data.
        :param bool xval: If True, return the precision value for each of the cross-validated splits.

        :returns: The precision values for the specified key(s).

        :examples:

        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> r = cars[0].runif()
        >>> train = cars[r > .2]
        >>> valid = cars[r <=.2]
        >>> response_col = "economy_20mpg"
        >>> distribution = "bernoulli"
        >>> predictors = ["displacement", "power", "weight", "acceleration", "year"]
        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution=distribution,
        ...                                    fold_assignment="Random")
        >>> gbm.train(y=response_col,
        ...           x=predictors,
        ...           validation_frame=valid,
        ...           training_frame=train)
        >>> gbm.precision(train=False, valid=False, xval=False) # <- Default: return training metric value
        >>> gbm.precision(train=True, valid=True, xval=True)
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm):
            m[k] = None if v is None else v.metric("precision", thresholds=thresholds)
        return list(m.values())[0] if len(m) == 1 else m


    def tpr(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the True Positive Rate for a set of thresholds.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: If None, then the thresholds in this set of metrics will be used.
        :param bool train: If True, return the TPR value for the training data.
        :param bool valid: If True, return the TPR value for the validation data.
        :param bool xval: If True, return the TPR value for each of the cross-validated splits.

        :returns: The TPR values for the specified key(s).

        :examples:

        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> r = cars[0].runif()
        >>> train = cars[r > .2]
        >>> valid = cars[r <=.2]
        >>> response_col = "economy_20mpg"
        >>> distribution = "bernoulli"
        >>> predictors = ["displacement", "power", "weight", "acceleration", "year"]
        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution=distribution,
        ...                                    fold_assignment="Random")
        >>> gbm.train(y=response_col,
        ...           x=predictors,
        ...           validation_frame=valid,
        ...           training_frame=train)
        >>> gbm.tpr(train=False, valid=False, xval=False) # <- Default: return training metric
        >>> gbm.tpr(train=True, valid=True, xval=True)
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm):
            m[k] = None if v is None else v.metric("tpr", thresholds=thresholds)
        return list(m.values())[0] if len(m) == 1 else m


    def tnr(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the True Negative Rate for a set of thresholds.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: If None, then the thresholds in this set of metrics will be used.
        :param bool train: If True, return the TNR value for the training data.
        :param bool valid: If True, return the TNR value for the validation data.
        :param bool xval: If True, return the TNR value for each of the cross-validated splits.

        :returns: The TNR values for the specified key(s).

        :examples:

        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> r = cars[0].runif()
        >>> train = cars[r > .2]
        >>> valid = cars[r <=.2]
        >>> response_col = "economy_20mpg"
        >>> distribution = "bernoulli"
        >>> predictors = ["displacement", "power", "weight", "acceleration", "year"]
        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution=distribution,
        ...                                    fold_assignment="Random")
        >>> gbm.train(y=response_col,
        ...           x=predictors,
        ...           validation_frame=valid,
        ...           training_frame=train)
        >>> gbm.tnr(train=False, valid=False, xval=False) # <- Default: return training metric
        >>> gbm.tnr(train=True, valid=True, xval=True)
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm):
            m[k] = None if v is None else v.metric("tnr", thresholds=thresholds)
        return list(m.values())[0] if len(m) == 1 else m


    def fnr(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the False Negative Rates for a set of thresholds.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: If None, then the thresholds in this set of metrics will be used.
        :param bool train: If True, return the FNR value for the training data.
        :param bool valid: If True, return the FNR value for the validation data.
        :param bool xval: If True, return the FNR value for each of the cross-validated splits.

        :returns: The FNR values for the specified key(s).

        :examples:

        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> r = cars[0].runif()
        >>> train = cars[r > .2]
        >>> valid = cars[r <= .2]
        >>> response_col = "economy_20mpg"
        >>> distribution = "bernoulli"
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> from h2o.estimators import H2OGradientBoostingEstimator
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution=distribution,
        ...                                    fold_assignment="Random")
        >>> gbm.train(y=response_col,
        ...           x=predictors,
        ...           validation_frame=valid,
        ...           training_frame=train)
        >>> gbm.fnr(train=False, valid=False, xval=False) # <- Default: return training metric
        >>> gbm.fnr(train=True, valid=True, xval=True)
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm):
            m[k] = None if v is None else v.metric("fnr", thresholds=thresholds)
        return list(m.values())[0] if len(m) == 1 else m


    def fpr(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the False Positive Rates for a set of thresholds.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: If None, then the thresholds in this set of metrics will be used.
        :param bool train: If True, return the FPR value for the training data.
        :param bool valid: If True, return the FPR value for the validation data.
        :param bool xval: If True, return the FPR value for each of the cross-validated splits.

        :returns: The FPR values for the specified key(s).

        :examples:

        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> r = cars[0].runif()
        >>> train = cars[r > .2]
        >>> valid = cars[r <= .2]
        >>> response_col = "economy_20mpg"
        >>> distribution = "bernoulli"
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> from h2o.estimators import H2OGradientBoostingEstimator
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution=distribution,
        ...                                    fold_assignment="Random")
        >>> gbm.train(y=response_col,
        ...           x=predictors,
        ...           validation_frame=valid,
        ...           training_frame=train)
        >>> gbm.frp(train=False, valid=False, xval=False) # <- Default: return training metric
        >>> gbm.fpr(train=True, valid=True, xval=True)
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm):
            m[k] = None if v is None else v.metric("fpr", thresholds=thresholds)
        return list(m.values())[0] if len(m) == 1 else m


    def recall(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the recall for a set of thresholds (aka True Positive Rate).

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: If None, then the thresholds in this set of metrics will be used.
        :param bool train: If True, return the recall value for the training data.
        :param bool valid: If True, return the recall value for the validation data.
        :param bool xval: If True, return the recall value for each of the cross-validated splits.

        :returns: The recall values for the specified key(s).

        :examples:

        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> r = cars[0].runif()
        >>> train = cars[r > .2]
        >>> valid = cars[r <= .2]
        >>> response_col = "economy_20mpg"
        >>> distribution = "bernoulli"
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> from h2o.estimators import H2OGradientBoostingEstimator
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution=distribution,
        ...                                    fold_assignment="Random")
        >>> gbm.train(y=response_col,
        ...           x=predictors,
        ...           validation_frame=valid,
        ...           training_frame=train)
        >>> gbm.recall(train=False, valid=False, xval=False) # <- Default: return training metric
        >>> gbm.recall(train=True, valid=True, xval=True)
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm):
            m[k] = None if v is None else v.metric("tpr", thresholds=thresholds)
        return list(m.values())[0] if len(m) == 1 else m


    def sensitivity(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the sensitivity for a set of thresholds (aka True Positive Rate or Recall).

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: If None, then the thresholds in this set of metrics will be used.
        :param bool train: If True, return the sensitivity value for the training data.
        :param bool valid: If True, return the sensitivity value for the validation data.
        :param bool xval: If True, return the sensitivity value for each of the cross-validated splits.

        :returns: The sensitivity values for the specified key(s).

        :examples:

        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> r = cars[0].runif()
        >>> train = cars[r > .2]
        >>> valid = cars[r <= .2]
        >>> response_col = "economy_20mpg"
        >>> distribution = "bernoulli"
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> from h2o.estimators import H2OGradientBoostingEstimator
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution=distribution,
        ...                                    fold_assignment="Random")
        >>> gbm.train(y=response_col,
        ...           x=predictors,
        ...           validation_frame=valid,
        ...           training_frame=train)
        >>> gbm.sensitivity(train=False, valid=False, xval=False) # <- Default: return training metric
        >>> gbm.sensitivity(train=True, valid=True, xval=True)
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm):
            m[k] = None if v is None else v.metric("tpr", thresholds=thresholds)
        return list(m.values())[0] if len(m) == 1 else m


    def fallout(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the fallout for a set of thresholds (aka False Positive Rate).

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: If None, then the thresholds in this set of metrics will be used.
        :param bool train: If True, return the fallout value for the training data.
        :param bool valid: If True, return the fallout value for the validation data.
        :param bool xval: If True, return the fallout value for each of the cross-validated splits.

        :returns: The fallout values for the specified key(s).

        :examples:

        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> r = cars[0].runif()
        >>> train = cars[r > .2]
        >>> valid = cars[r <= .2]
        >>> response_col = "economy_20mpg"
        >>> distribution = "bernoulli"
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> from h2o.estimators import H2OGradientBoostingEstimator
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution=distribution,
        ...                                    fold_assignment="Random")
        >>> gbm.train(y=response_col,
        ...           x=predictors,
        ...           validation_frame=valid,
        ...           training_frame=train)
        >>> gbm.fallout(train=False, valid=False, xval=False) # <- Default: return training metric
        >>> gbm.fallout(train=True, valid=True, xval=True)
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm):
            m[k] = None if v is None else v.metric("fpr", thresholds=thresholds)
        return list(m.values())[0] if len(m) == 1 else m


    def missrate(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the miss rate for a set of thresholds (aka False Negative Rate).

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: If None, then the thresholds in this set of metrics will be used.
        :param bool train: If True, return the miss rate value for the training data.
        :param bool valid: If True, return the miss rate value for the validation data.
        :param bool xval: If True, return the miss rate value for each of the cross-validated splits.

        :returns: The miss rate values for the specified key(s).

        :examples:

        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> r = cars[0].runif()
        >>> train = cars[r > .2]
        >>> valid = cars[r <= .2]
        >>> response_col = "economy_20mpg"
        >>> distribution = "bernoulli"
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> from h2o.estimators import H2OGradientBoostingEstimator
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution=distribution,
        ...                                    fold_assignment="Random")
        >>> gbm.train(y=response_col,
        ...           x=predictors,
        ...           validation_frame=valid,
        ...           training_frame=train)
        >>> gbm.missrate(train=False, valid=False, xval=False) # <- Default: return training metric
        >>> gbm.missrate(train=True, valid=True, xval=True)
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm):
            m[k] = None if v is None else v.metric("fnr", thresholds=thresholds)
        return list(m.values())[0] if len(m) == 1 else m


    def specificity(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the specificity for a set of thresholds (aka True Negative Rate).

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: If None, then the thresholds in this set of metrics will be used.
        :param bool train: If True, return the specificity value for the training data.
        :param bool valid: If True, return the specificity value for the validation data.
        :param bool xval: If True, return the specificity value for each of the cross-validated splits.

        :returns: The specificity values for the specified key(s).

        :examples:

        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> r = cars[0].runif()
        >>> train = cars[r > .2]
        >>> valid = cars[r <=.2]
        >>> response_col = "economy_20mpg"
        >>> distribution = "bernoulli"
        >>> predictors = ["displacement", "power", "weight", "acceleration", "year"]
        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution=distribution,
        ...                                    fold_assignment="Random")
        >>> gbm.train(y=response_col,
        ...           x=predictors,
        ...           validation_frame=valid,
        ...           training_frame=train)
        >>> gbm.specificity(train=False, valid=False, xval=False) # <- Default: return training metric
        >>> gbm.specificity(train=True, valid=True, xval=True)
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm):
            m[k] = None if v is None else v.metric("tnr", thresholds=thresholds)
        return list(m.values())[0] if len(m) == 1 else m


    def mcc(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the MCC for a set of thresholds.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: If None, then the thresholds in this set of metrics will be used.
        :param bool train: If True, return the MCC value for the training data.
        :param bool valid: If True, return the MCC value for the validation data.
        :param bool xval: If True, return the MCC value for each of the cross-validated splits.

        :returns: The MCC values for the specified key(s).

        :examples:

        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> r = cars[0].runif()
        >>> train = cars[r > .2]
        >>> valid = cars[r <=.2]
        >>> response_col = "economy_20mpg"
        >>> distribution = "bernoulli"
        >>> predictors = ["displacement", "power", "weight", "acceleration", "year"]
        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution=distribution,
        ...                                    fold_assignment="Random")
        >>> gbm.train(y=response_col,
        ...           x=predictors,
        ...           validation_frame=valid,
        ...           training_frame=train)
        >>> gbm.mcc(train=False, valid=False, xval=False) # <- Default: return training metric value
        >>> gbm.mcc(train=True, valid=True, xval=True)
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm):
            m[k] = None if v is None else v.metric("absolute_mcc", thresholds=thresholds)
        return list(m.values())[0] if len(m) == 1 else m


    def max_per_class_error(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the max per class error for a set of thresholds.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: If None, then the thresholds in this set of metrics will be used.
        :param bool train: If True, return the max per class error value for the training data.
        :param bool valid: If True, return the max per class error value for the validation data.
        :param bool xval: If True, return the max per class error value for each of the cross-validated splits.

        :returns: The max per class error values for the specified key(s).

        :examples:

        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> r = cars[0].runif()
        >>> train = cars[r > .2]
        >>> valid = cars[r <=.2]
        >>> response_col = "economy_20mpg"
        >>> distribution = "bernoulli"
        >>> predictors = ["displacement", "power", "weight", "acceleration", "year"]
        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution=distribution,
        ...                                    fold_assignment="Random")
        >>> gbm.train(y=response_col,
        ...           x=predictors,
        ...           validation_frame=valid,
        ...           training_frame=train)
        >>> gbm.max_per_class_error(train=False, valid=False, xval=False) # <- Default: return training metric value
        >>> gbm.max_per_class_error(train=True, valid=True, xval=True)
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm):
            m[k] = None if v is None else [[mpca[0], 1 - mpca[1]] for mpca in v.metric(
                "min_per_class_accuracy", thresholds=thresholds)]
        return list(m.values())[0] if len(m) == 1 else m


    def mean_per_class_error(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the mean per class error for a set of thresholds.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: If None, then the thresholds in this set of metrics will be used.
        :param bool train: If True, return the mean per class error value for the training data.
        :param bool valid: If True, return the mean per class error value for the validation data.
        :param bool xval: If True, return the mean per class error value for each of the cross-validated splits.

        :returns: The mean per class error values for the specified key(s).

        :examples:

        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> r = cars[0].runif()
        >>> train = cars[r > .2]
        >>> valid = cars[r <= .2]
        >>> response_col = "economy_20mpg"
        >>> distribution = "bernoulli"
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> from h2o.estimators import H2OGradientBoostingEstimator
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution=distribution,
        ...                                    fold_assignment="Random")
        >>> gbm.train(y=response_col,
        ...           x=predictors,
        ...           validation_frame=valid,
        ...           training_frame=train)
        >>> gbm.mean_per_class_error(train=False, valid=False, xval=False) # <- Default: return training metric
        >>> gbm.mean_per_class_error(train=True, valid=True, xval=True)
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm):
            if v is None:
                m[k] = None
            else:
                m[k] = [[mpca[0], 1 - mpca[1]] for mpca in v.metric("mean_per_class_accuracy", thresholds=thresholds)]
        return list(m.values())[0] if len(m) == 1 else m


    def metric(self, metric, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the metric value for a set of thresholds.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param str metric: name of the metric to retrieve.
        :param thresholds: If None, then the thresholds in this set of metrics will be used.
        :param bool train: If True, return the metric value for the training data.
        :param bool valid: If True, return the metric value for the validation data.
        :param bool xval: If True, return the metric value for each of the cross-validated splits.

        :returns: The metric values for the specified key(s).

        :examples:

        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> r = cars[0].runif()
        >>> train = cars[r > .2]
        >>> valid = cars[r <= .2]
        >>> response_col = "economy_20mpg"
        >>> distribution = "bernoulli"
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        # thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99])
        >>> thresholds = [0.01,0.5,0.99]
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution=distribution,
        ...                                    fold_assignment="Random")
        >>> gbm.train(y=response_col,
        ...           x=predictors,
        ...           validation_frame=valid,
        ...           training_frame=train)
        # allowable metrics are absolute_mcc, accuracy, precision,
        # f0point5, f1, f2, mean_per_class_accuracy, min_per_class_accuracy,
        # tns, fns, fps, tps, tnr, fnr, fpr, tpr, recall, sensitivity,
        # missrate, fallout, specificity
        >>> gbm.metric(metric='tpr', thresholds=thresholds)
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm):
            m[k] = None if v is None else v.metric(metric, thresholds)
        return list(m.values())[0] if len(m) == 1 else m


    def plot(self, timestep="AUTO", metric="AUTO", server=False, **kwargs):
        """
        Plot training set (and validation set if available) scoring history for an H2OBinomialModel.

        The timestep and metric arguments are restricted to what is available in its scoring history.

        :param str timestep: A unit of measurement for the x-axis.
        :param str metric: A unit of measurement for the y-axis.
        :param bool server: if True, then generate the image inline (using matplotlib's "Agg" backend)

        :examples:

        >>> air = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
        >>> airlines["Year"] = airlines["Year"].asfactor()
        >>> airlines["Month"] = airlines["Month"].asfactor()
        >>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
        >>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
        >>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
        >>> myX = ["Origin", "Dest", "Distance", "UniqueCarrier",
        ...        "Month", "DayofMonth", "DayOfWeek"]
        >>> myY = "IsDepDelayed"
        >>> train, valid = airlines.split_frame(ratios=[.8], seed=1234)
        >>> air_gbm = H2OGradientBoostingEstimator(distribution="bernoulli",
        ...                                        ntrees=100,
        ...                                        max_depth=3,
        ...                                        learn_rate=0.01)
        >>> air_gbm.train(x=myX,
        ...               y=myY,
        ...               training_frame=train,
        ...               validation_frame=valid)
        >>> air_gbm.plot(type="roc", train=True, server=True)
        >>> air_gbm.plot(type="roc", valid=True, server=True)
        >>> perf = air_gbm.model_performance(air_test)
        >>> perf.plot(type="roc", server=True)
        >>> perf.plot
        """
        assert_is_type(metric, "AUTO", "logloss", "auc", "classification_error", "rmse")
        if self._model_json["algo"] in ("deeplearning", "deepwater", "xgboost", "drf", "gbm"):
            if metric == "AUTO":
                metric = "logloss"
        self._plot(timestep=timestep, metric=metric, server=server)


    def roc(self, train=False, valid=False, xval=False):
        """
        Return the coordinates of the ROC curve for a given set of data.

        The coordinates are two-tuples containing the false positive rates as a list and true positive rates as a list.
        If all are False (default), then return is the training data. If more than one ROC
        curve is requested, the data is returned as a dictionary of two-tuples.

        :param bool train: If True, return the ROC value for the training data.
        :param bool valid: If True, return the ROC value for the validation data.
        :param bool xval: If True, return the ROC value for each of the cross-validated splits.

        :returns: The ROC values for the specified key(s).

        :examples:

        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> r = cars[0].runif()
        >>> train = cars[r > .2]
        >>> valid = cars[r <=.2]
        >>> response_col = "economy_20mpg"
        >>> distribution = "bernoulli"
        >>> predictors = ["displacement", "power", "weight", "acceleration", "year"]
        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution=distribution,
        ...                                    fold_assignment="Random")
        >>> gbm.train(y=response_col,
        ...           x=predictors,
        ...           validation_frame=valid,
        ...           training_frame=train)
        >>> gbm.roc(train=False, valid=False, xval=False) # <- Default: return training data
        >>> gbm.roc(train=True, valid=True, xval=True)
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm):

            if v is not None:
                m[k] = (v.fprs, v.tprs)
        return list(m.values())[0] if len(m) == 1 else m


    def gains_lift(self, train=False, valid=False, xval=False):
        """
        Get the Gains/Lift table for the specified metrics.

        If all are False (default), then return the training metric Gains/Lift table.
        If more than one options is set to True, then return a dictionary of metrics where t
        he keys are "train", "valid", and "xval".

        :param bool train: If True, return the gains lift value for the training data.
        :param bool valid: If True, return the gains lift value for the validation data.
        :param bool xval: If True, return the gains lift value for each of the cross-validated splits.

        :returns: The gains lift values for the specified key(s).

        :examples:

        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> r = cars[0].runif()
        >>> train = cars[r > .2]
        >>> valid = cars[r <=.2]
        >>> response_col = "economy_20mpg"
        >>> distribution = "bernoulli"
        >>> predictors = ["displacement", "power", "weight", "acceleration", "year"]
        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution=distribution,
        ...                                    fold_assignment="Random")
        >>> gbm.train(y=response_col,
        ...           x=predictors,
        ...           validation_frame=valid,
        ...           training_frame=train)
        >>> gbm.gains_lift(train=False, valid=False, xval=False) # <- Default: return training metric Gain/Lift table
        >>> gbm.gains_lift(train=True, valid=True, xval=True)
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm):
            m[k] = None if v is None else v.gains_lift()
        return list(m.values())[0] if len(m) == 1 else m


    def confusion_matrix(self, metrics=None, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the confusion matrix for the specified metrics/thresholds.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the
        keys are "train", "valid", and "xval"

        :param metrics: One or more of ``"min_per_class_accuracy"``, ``"absolute_mcc"``, ``"tnr"``, ``"fnr"``,
            ``"fpr"``, ``"tpr"``, ``"precision"``, ``"accuracy"``, ``"f0point5"``, ``"f2"``, ``"f1"``.
        :param thresholds: If None, then the thresholds in this set of metrics will be used.
        :param bool train: If True, return the confusion matrix value for the training data.
        :param bool valid: If True, return the confusion matrix value for the validation data.
        :param bool xval: If True, return the confusion matrix value for each of the cross-validated splits.

        :returns: The confusion matrix values for the specified key(s).

        :examples:

        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> r = cars[0].runif()
        >>> train = cars[r > .2]
        >>> valid = cars[r <=.2]
        >>> response_col = "economy_20mpg"
        >>> distribution = "bernoulli"
        >>> predictors = ["displacement", "power", "weight", "acceleration", "year"]
        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution=distribution,
        ...                                    fold_assignment="Random")
        >>> gbm.train(y=response_col,
        ...           x=predictors,
        ...           validation_frame=valid,
        ...           training_frame=train)
        >>> gbm.confusion_matrix(train=False, valid=False, xval=False) # <- Default: return training metric value
        >>> gbm.confusion_matric(train=True, valid=True, xval=True)
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm):
            m[k] = None if v is None else v.confusion_matrix(metrics=metrics, thresholds=thresholds)
        return list(m.values())[0] if len(m) == 1 else m


    def find_threshold_by_max_metric(self, metric, train=False, valid=False, xval=False):
        """
        If all are False (default), then return the training metric value.

        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param str metric: The metric to search for.
        :param bool train: If True, return the find threshold by max metric value for the training data.
        :param bool valid: If True, return the find threshold by max metric value for the validation data.
        :param bool xval: If True, return the find threshold by max metric value for each of the cross-validated splits.

        :returns: The find threshold by max metric values for the specified key(s).

        :examples:

        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> r = cars[0].runif()
        >>> train = cars[r > .2]
        >>> valid = cars[r <=.2]
        >>> response_col = "economy_20mpg"
        >>> distribution = "bernoulli"
        >>> predictors = ["displacement", "power", "weight",
        ...               "acceleration", "year"]
        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution=distribution,
        ...                                    fold_assignment="Random")
        >>> gbm.train(y=response_col,
        ...           x=predictors,
        ...           validation_frame=valid,
        ...           training_frame=train)
        >>> max_metric = gbm.find_threshold_by_max_metric(metric="f2",
        ...                                               train=True)
        >>> max_metric
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm):
            m[k] = None if v is None else v.find_threshold_by_max_metric(metric)
        return list(m.values())[0] if len(m) == 1 else m


    def find_idx_by_threshold(self, threshold, train=False, valid=False, xval=False):
        """
        Retrieve the index in this metric's threshold list at which the given threshold is located.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param float threshold: Threshold value to search for in the threshold list.
        :param bool train: If True, return the find idx by threshold value for the training data.
        :param bool valid: If True, return the find idx by threshold value for the validation data.
        :param bool xval: If True, return the find idx by threshold value for each of the cross-validated splits.

        :returns: The find idx by threshold values for the specified key(s).

        :examples:

        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> r = cars[0].runif()
        >>> train = cars[r > .2]
        >>> valid = cars[r <=.2]
        >>> response_col = "economy_20mpg"
        >>> distribution = "bernoulli"
        >>> predictors = ["displacement", "power", "weight",
        ...               "acceleration", "year"]
        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution=distribution,
        ...                                    fold_assignment="Random")
        >>> gbm.train(y=response_col,
        ...           x=predictors,
        ...           validation_frame=valid,
        ...           training_frame=train)
        >>> idx_threshold = gbm.find_idx_by_threshold(threshold=0.39438,
        ...                                           train=True)
        >>> idx_threshold
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm):
            m[k] = None if v is None else v.find_idx_by_threshold(threshold)
        return list(m.values())[0] if len(m) == 1 else m
