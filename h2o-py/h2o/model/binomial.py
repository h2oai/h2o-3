# -*- encoding: utf-8 -*-
from __future__ import absolute_import, division, print_function, unicode_literals
from h2o.utils.compatibility import *  # NOQA
from h2o.utils.compatibility import viewitems
from h2o.utils.typechecks import assert_is_type
from .model_base import ModelBase
from h2o.exceptions import H2OValueError


class H2OBinomialModel(ModelBase):

    def F1(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the F1 value for a set of thresholds.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where
        the keys are "train", "valid", and "xval".

        :param thresholds: If None, then the threshold maximizing the metric will be used.
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
        >>> gbm.F1()# <- Default: return training metric value
        >>> gbm.F1(train=True,  valid=True,  xval=True)
        """
        return self.metric('f1', thresholds, train, valid, xval)


    def F2(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the F2 for a set of thresholds.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: If None, then the threshold maximizing the metric will be used.
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
        >>> gbm.F2() # <- Default: return training metric value
        >>> gbm.F2(train=True, valid=True, xval=True)
        """
        return self.metric('f2', thresholds, train, valid, xval)


    def F0point5(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the F0.5 for a set of thresholds.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: If None, then the threshold maximizing the metric will be used.
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
        >>> F0point5 = gbm.F0point5() # <- Default: return training metric value
        >>> F0point5 = gbm.F0point5(train=True,  valid=True,  xval=True)
        """
        return self.metric('f0point5', thresholds, train, valid, xval)


    def accuracy(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the accuracy for a set of thresholds.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: If None, then the threshold maximizing the metric will be used.
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
        >>> gbm.accuracy() # <- Default: return training metric value
        >>> gbm.accuracy(train=True, valid=True, xval=True)
        """
        return self.metric('accuracy', thresholds, train, valid, xval)


    def error(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the error for a set of thresholds.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: If None, then the threshold minimizing the error will be used.
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
        >>> gbm.error() # <- Default: return training metric
        >>> gbm.error(train=True, valid=True, xval=True)
        """
        return self.metric('error', thresholds, train, valid, xval)


    def precision(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the precision for a set of thresholds.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: If None, then the threshold maximizing the metric will be used.
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
        >>> gbm.precision() # <- Default: return training metric value
        >>> gbm.precision(train=True, valid=True, xval=True)
        """
        return self.metric('precision', thresholds, train, valid, xval)


    def tpr(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the True Positive Rate for a set of thresholds.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: If None, then the threshold maximizing the metric will be used.
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
        >>> gbm.tpr() # <- Default: return training metric
        >>> gbm.tpr(train=True, valid=True, xval=True)
        """
        return self.metric('tpr', thresholds, train, valid, xval)


    def tnr(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the True Negative Rate for a set of thresholds.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: If None, then the threshold maximizing the metric will be used.
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
        >>> gbm.tnr() # <- Default: return training metric
        >>> gbm.tnr(train=True, valid=True, xval=True)
        """
        return self.metric('tnr', thresholds, train, valid, xval)


    def fnr(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the False Negative Rates for a set of thresholds.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: If None, then the threshold maximizing the metric will be used.
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
        >>> gbm.fnr() # <- Default: return training metric
        >>> gbm.fnr(train=True, valid=True, xval=True)
        """
        return self.metric('fnr', thresholds, train, valid, xval)


    def fpr(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the False Positive Rates for a set of thresholds.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: If None, then the threshold maximizing the metric will be used.
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
        >>> gbm.fpr() # <- Default: return training metric
        >>> gbm.fpr(train=True, valid=True, xval=True)
        """
        return self.metric('fpr', thresholds, train, valid, xval)


    def recall(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the recall for a set of thresholds (aka True Positive Rate).

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: If None, then the threshold maximizing the metric will be used.
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
        >>> gbm.recall() # <- Default: return training metric
        >>> gbm.recall(train=True, valid=True, xval=True)
        """
        return self.metric('recall', thresholds, train, valid, xval)


    def sensitivity(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the sensitivity for a set of thresholds (aka True Positive Rate or Recall).

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: If None, then the threshold maximizing the metric will be used.
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
        >>> gbm.sensitivity() # <- Default: return training metric
        >>> gbm.sensitivity(train=True, valid=True, xval=True)
        """
        return self.metric('sensitivity', thresholds, train, valid, xval)


    def fallout(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the fallout for a set of thresholds (aka False Positive Rate).

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: If None, then the threshold maximizing the metric will be used.
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
        >>> gbm.fallout() # <- Default: return training metric
        >>> gbm.fallout(train=True, valid=True, xval=True)
        """
        return self.metric('fallout', thresholds, train, valid, xval)


    def missrate(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the miss rate for a set of thresholds (aka False Negative Rate).

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: If None, then the threshold maximizing the metric will be used.
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
        >>> gbm.missrate() # <- Default: return training metric
        >>> gbm.missrate(train=True, valid=True, xval=True)
        """
        return self.metric('missrate', thresholds, train, valid, xval)


    def specificity(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the specificity for a set of thresholds (aka True Negative Rate).

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: If None, then the threshold maximizing the metric will be used.
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
        >>> gbm.specificity() # <- Default: return training metric
        >>> gbm.specificity(train=True, valid=True, xval=True)
        """
        return self.metric('specificity', thresholds, train, valid, xval)


    def mcc(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the MCC for a set of thresholds.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: If None, then the threshold maximizing the metric will be used.
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
        >>> gbm.mcc() # <- Default: return training metric value
        >>> gbm.mcc(train=True, valid=True, xval=True)
        """
        return self.metric('mcc', thresholds, train, valid, xval)


    def max_per_class_error(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the max per class error for a set of thresholds.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: If None, then the threshold minimizing the error will be used.
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
        >>> gbm.max_per_class_error() # <- Default: return training metric value
        >>> gbm.max_per_class_error(train=True, valid=True, xval=True)
        """
        return self.metric('max_per_class_error', thresholds, train, valid, xval)


    def mean_per_class_error(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the mean per class error for a set of thresholds.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: If None, then the threshold minimizing the error will be used.
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
        >>> gbm.mean_per_class_error() # <- Default: return training metric
        >>> gbm.mean_per_class_error(train=True, valid=True, xval=True)
        """
        return self.metric('mean_per_class_error', thresholds, train, valid, xval)


    def metric(self, metric, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the metric value for a set of thresholds.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param str metric: name of the metric to retrieve.
        :param thresholds: If None, then the threshold maximizing the metric will be used (or minimizing it if the metric is an error).
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
            if v is None:
                m[k] = None
            elif hasattr(v, metric) and callable(getattr(v, metric)):
                m[k] = getattr(v, metric)(thresholds=thresholds)
            else:
                m[k] = v.metric(metric, thresholds=thresholds)
        return list(m.values())[0] if len(m) == 1 else m


    def plot(self, timestep="AUTO", metric="AUTO", server=False, **kwargs):
        """
        Plot training set (and validation set if available) scoring history for an H2OBinomialModel.

        The timestep and metric arguments are restricted to what is available in its scoring history.

        :param str timestep: A unit of measurement for the x-axis.
        :param str metric: A unit of measurement for the y-axis.
        :param bool server: if True, then generate the image inline (using matplotlib's "Agg" backend)

        :examples:

        >>> from h2o.estimators import H2OGeneralizedLinearEstimator
        >>> benign = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/benign.csv")
        >>> response = 3
        >>> predictors = [0, 1, 2, 4, 5, 6, 7, 8, 9, 10]
        >>> model = H2OGeneralizedLinearEstimator(family="binomial")
        >>> model.train(x=predictors, y=response, training_frame=benign)
        >>> model.plot(timestep="AUTO", metric="objective", server=False)
        """
        assert_is_type(metric, "AUTO", "logloss", "auc", "classification_error", "rmse", "objective", 
                       "negative_log_likelihood")
        if self._model_json["algo"] in ("deeplearning", "xgboost", "drf", "gbm"):
            # make sure metric is not those of GLM metrics for other models
            if metric in ("negative_log_likelihood", "objective"):
                raise H2OValueError("Metrics: negative_log_likelihood, objective are only for glm models.")
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
        >>> gbm.roc() # <- Default: return training data
        >>> gbm.roc(train=True, valid=True, xval=True)
        """
        return self._delegate_to_metrics('roc', train, valid, xval)


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
        >>> gbm.gains_lift() # <- Default: return training metric Gain/Lift table
        >>> gbm.gains_lift(train=True, valid=True, xval=True)
        """
        return self._delegate_to_metrics('gains_lift', train, valid, xval)


    def confusion_matrix(self, metrics=None, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the confusion matrix for the specified metrics/thresholds.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the
        keys are "train", "valid", and "xval"

        :param metrics: A string (or list of strings) among metrics listed in :const:`H2OBinomialModelMetrics.maximizing_metrics`.
            Defaults to 'f1'.
        :param thresholds: A value (or list of values) between 0 and 1.
            If None, then the thresholds maximizing each provided metric will be used.
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
        >>> gbm.confusion_matrix() # <- Default: return training metric value
        >>> gbm.confusion_matrix(train=True, valid=True, xval=True)
        """
        return self._delegate_to_metrics('confusion_matrix', train, valid, xval,
                                         metrics=metrics, thresholds=thresholds)


    def find_threshold_by_max_metric(self, metric, train=False, valid=False, xval=False):
        """
        If all are False (default), then return the training metric value.

        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param str metric: A metric among the metrics listed in :const:`H2OBinomialModelMetrics.maximizing_metrics`.
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
        return self._delegate_to_metrics('find_threshold_by_max_metric', train, valid, xval, metric=metric)


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
        return self._delegate_to_metrics('find_idx_by_threshold', train, valid, xval, threshold=threshold)


    def _delegate_to_metrics(self, method, train=False, valid=False, xval=False, **kwargs):
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm):
            if v is None:
                m[k] = None
            elif hasattr(v, method) and callable(getattr(v, method)):
                m[k] = getattr(v, method)(**kwargs)
            else:
                raise ValueError('no method {} in {}'.format(method, type(v)))
        return list(m.values())[0] if len(m) == 1 else m
