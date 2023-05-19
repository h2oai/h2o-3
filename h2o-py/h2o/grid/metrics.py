# -*- encoding: utf-8 -*-
"""
.. note::
    Classes in this module are used at runtime as mixins: their methods can (and should) be accessed directly from a trained grid.
"""


#-----------------------------------------------------------------------------------------------------------------------
# AutoEncoder Grid Search
#-----------------------------------------------------------------------------------------------------------------------

class H2OAutoEncoderGridSearch(object):

    def anomaly(self, test_data, per_feature=False):
        """
        Obtain the reconstruction error for the input ``test_data``.

        :param H2OFrame test_data: The dataset upon which the reconstruction error is computed.
        :param bool per_feature: Whether to return the square reconstruction error per feature. Otherwise, return
            the mean square error.
        :returns: the reconstruction error.

        :example:

        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> from h2o.estimators import H2OAutoEncoderEstimator
        >>> rows = [[1,2,3,4,0]*50,
        ...         [2,1,2,4,1]*50,
        ...         [2,1,4,2,1]*50,
        ...         [0,1,2,34,1]*50,
        ...         [2,3,4,1,0]*50]
        >>> fr = h2o.H2OFrame(rows)
        >>> hyper_parameters = {'activation': "Tanh", 'hidden': [50,50,50]}
        >>> gs = H2OGridSearch(H2OAutoEncoderEstimator(), hyper_parameters)
        >>> gs.train(x=range(4), training_frame=fr)
        >>> gs.anomaly(fr, per_feature=True)
        """
        return {model.model_id: model.anomaly(test_data, per_feature) for model in self.models}



#-----------------------------------------------------------------------------------------------------------------------
# Binomial Grid Search
#-----------------------------------------------------------------------------------------------------------------------

class H2OBinomialGridSearch(object):

    def F1(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the F1 values for a set of thresholds for the models explored.

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where
        the keys are "train", "valid", and "xval".

        :param thresholds: thresholds parameter must be a list (e.g. ``[0.01, 0.5, 0.99]``).
            If None, then the threshold maximizing the metric will be used.
        :param bool train: If ``True``, return the F1 value for the training data.
        :param bool valid: If ``True``, return the F1 value for the validation data.
        :param bool xval: If ``True``, return the F1 value for each of the cross-validated splits.
        :returns: Dictionary of model keys to F1 values

        :examples:

        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
        >>> training_data = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/benign.csv")
        >>> hyper_parameters = {'alpha': [0.01,0.5], 'lambda': [1e-5,1e-6]}
        >>> gs = H2OGridSearch(H2OGeneralizedLinearEstimator(family='binomial'),
        ...                                                  hyper_parameters)
        >>> gs.train(x=[3, 4-11],
        ...          y=3,
        ...          training_frame=training_data)
        >>> gs.F1(train=True)
        """
        return {model.model_id: model.F1(thresholds, train, valid, xval) for model in
                self.models}  # dict model key -> F1 score


    def F2(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the F2 for a set of thresholds.

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: thresholds parameter must be a list (e.g. ``[0.01, 0.5, 0.99]``).
            If None, then the threshold maximizing the metric will be used.
        :param bool train: If train is ``True``, then return the F2 value for the training data.
        :param bool valid: If valid is ``True``, then return the F2 value for the validation data.
        :param bool xval:  If xval is ``True``, then return the F2 value for the cross validation data.
        :returns: Dictionary of model keys to F2 values.

        :examples:

        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
        >>> training_data = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/benign.csv")
        >>> hyper_parameters = {'alpha': [0.01,0.5], 'lambda': [1e-5,1e-6]}
        >>> gs = H2OGridSearch(H2OGeneralizedLinearEstimator(family='binomial'),
        ...                                                  hyper_parameters)
        >>> gs.train(x=[3, 4-11],
        ...          y=3,
        ...          training_frame=training_data)
        >>> gs.F2(train=True)
        """
        return {model.model_id: model.F2(thresholds, train, valid, xval) for model in self.models}


    def F0point5(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the F0.5 for a set of thresholds.

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: thresholds parameter must be a list (e.g. ``[0.01, 0.5, 0.99]``).
            If None, then the threshold maximizing the metric will be used.
        :param bool train: If train is ``True``, then return the F0point5 value for the training data.
        :param bool valid: If valid is ``True``, then return the F0point5 value for the validation data.
        :param bool xval:  If xval is ``True``, then return the F0point5 value for the cross validation data.
        :returns: The F0point5 for this binomial model.

        :examples:

        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
        >>> training_data = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/benign.csv")
        >>> hyper_parameters = {'alpha': [0.01,0.5], 'lambda': [1e-5,1e-6]}
        >>> gs = H2OGridSearch(H2OGeneralizedLinearEstimator(family='binomial'),
        ...                                                  hyper_parameters)
        >>> gs.train(x=[3, 4-11],
        ...          y=3,
        ...          training_frame=training_data)
        >>> gs.F0point5(train=True)
        """
        return {model.model_id: model.F0point5(thresholds, train, valid, xval) for model in self.models}


    def accuracy(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the accuracy for a set of thresholds.

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: thresholds parameter must be a list (e.g. ``[0.01, 0.5, 0.99]``).
            If None, then the threshold maximizing the metric will be used.
        :param bool train: If train is ``True``, then return the accuracy value for the training data.
        :param bool valid: If valid is ``True``, then return the accuracy value for the validation data.
        :param bool xval:  If xval is ``True``, then return the accuracy value for the cross validation data.
        :returns: The accuracy for this binomial model.

        :examples:

        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
        >>> training_data = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/benign.csv")
        >>> hyper_parameters = {'alpha': [0.01,0.5], 'lambda': [1e-5,1e-6]}
        >>> gs = H2OGridSearch(H2OGeneralizedLinearEstimator(family='binomial'),
        ...                                                  hyper_parameters)
        >>> gs.train(x=[3, 4-11],
        ...          y=3,
        ...          training_frame=training_data)
        >>> gs.accuracy(train=True)
        """
        return {model.model_id: model.accuracy(thresholds, train, valid, xval) for model in self.models}


    def error(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the error for a set of thresholds.

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: thresholds parameter must be a list (e.g. ``[0.01, 0.5, 0.99]``).
            If None, then the threshold minimizing the error will be used.
        :param bool train: If train is ``True``, then return the error value for the training data.
        :param bool valid: If valid is ``True``, then return the error value for the validation data.
        :param bool xval:  If xval is ``True``, then return the error value for the cross validation data.
        :returns: The error for this binomial model.

        :examples:

        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
        >>> training_data = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/benign.csv")
        >>> hyper_parameters = {'alpha': [0.01,0.5], 'lambda': [1e-5,1e-6]}
        >>> gs = H2OGridSearch(H2OGeneralizedLinearEstimator(family='binomial'),
        ...                                                  hyper_parameters)
        >>> gs.train(x=[3, 4-11],
        ...          y=3,
        ...          training_frame=training_data)
        >>> gs.error(train=True)
        """
        return {model.model_id: model.error(thresholds, train, valid, xval) for model in self.models}


    def precision(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the precision for a set of thresholds.

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: thresholds parameter must be a list (e.g. ``[0.01, 0.5, 0.99]``).
            If None, then the threshold maximizing the metric will be used.
        :param bool train: If train is ``True``, then return the precision value for the training data.
        :param bool valid: If valid is ``True``, then return the precision value for the validation data.
        :param bool xval:  If xval is ``True``, then return the precision value for the cross validation data.
        :returns: The precision for this binomial model.

        :examples:

        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
        >>> training_data = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/benign.csv")
        >>> hyper_parameters = {'alpha': [0.01,0.5], 'lambda': [1e-5,1e-6]}
        >>> gs = H2OGridSearch(H2OGeneralizedLinearEstimator(family='binomial'),
        ...                                                  hyper_parameters)
        >>> gs.train(x=[3, 4-11],
        ...          y=3,
        ...          training_frame=training_data)
        >>> gs. precision(train=True)
        """
        return {model.model_id: model.precision(thresholds, train, valid, xval) for model in self.models}


    def tpr(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the True Positive Rate for a set of thresholds.

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: thresholds parameter must be a list (e.g. ``[0.01, 0.5, 0.99]``).
            If None, then the threshold maximizing the metric will be used.
        :param bool train: If train is ``True``, then return the TPR value for the training data.
        :param bool valid: If valid is ``True``, then return the TPR value for the validation data.
        :param bool xval:  If xval is ``True``, then return the TPR value for the cross validation data.
        :returns: The TPR for this binomial model.

        :examples:

        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
        >>> training_data = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/benign.csv")
        >>> hyper_parameters = {'alpha': [0.01,0.5], 'lambda': [1e-5,1e-6]}
        >>> gs = H2OGridSearch(H2OGeneralizedLinearEstimator(family='binomial'),
        ...                                                  hyper_parameters)
        >>> gs.train(x=[3, 4-11],
        ...          y=3,
        ...          training_frame=training_data)
        >>> gs.tpr(train=True)
        """
        return {model.model_id: model.tpr(thresholds, train, valid, xval) for model in self.models}


    def tnr(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the True Negative Rate for a set of thresholds.

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: thresholds parameter must be a list (e.g. ``[0.01, 0.5, 0.99]``).
            If None, then the threshold maximizing the metric will be used.
        :param bool train: If train is ``True``, then return the TNR value for the training data.
        :param bool valid: If valid is ``True``, then return the TNR value for the validation data.
        :param bool xval:  If xval is ``True``, then return the TNR value for the cross validation data.
        :returns: The TNR for this binomial model.

        :examples:

        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
        >>> training_data = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/benign.csv")
        >>> hyper_parameters = {'alpha': [0.01,0.5], 'lambda': [1e-5,1e-6]}
        >>> gs = H2OGridSearch(H2OGeneralizedLinearEstimator(family='binomial'),
        ...                                                  hyper_parameters)
        >>> gs.train(x=[3, 4-11],
        ...          y=3,
        ...          training_frame=training_data)
        >>> gs.tnr(train=True)
        """
        return {model.model_id: model.tnr(thresholds, train, valid, xval) for model in self.models}


    def fnr(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the False Negative Rates for a set of thresholds.
        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: thresholds parameter must be a list (e.g. ``[0.01, 0.5, 0.99]``).
            If None, then the threshold maximizing the metric will be used.
        :param bool train: If train is ``True``, then return the FNR value for the training data.
        :param bool valid: If valid is ``True``, then return the FNR value for the validation data.
        :param bool xval:  If xval is ``True``, then return the FNR value for the cross validation data.
        :returns: The FNR for this binomial model.

        :examples:

        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
        >>> training_data = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/benign.csv")
        >>> hyper_parameters = {'alpha': [0.01,0.5], 'lambda': [1e-5,1e-6]}
        >>> gs = H2OGridSearch(H2OGeneralizedLinearEstimator(family='binomial'),
        ...                                                  hyper_parameters)
        >>> gs.train(x=[3, 4-11],
        ...          y=3,
        ...          training_frame=training_data)
        >>> gs.fnr(train=True)
        """
        return {model.model_id: model.fnr(thresholds, train, valid, xval) for model in self.models}


    def fpr(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the False Positive Rates for a set of thresholds.

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: thresholds parameter must be a list (e.g. ``[0.01, 0.5, 0.99]``).
            If None, then the threshold maximizing the metric will be used.
        :param bool train: If train is ``True``, then return the FPR value for the training data.
        :param bool valid: If valid is ``True``, then return the FPR value for the validation data.
        :param bool xval:  If xval is ``True``, then return the FPR value for the cross validation data.
        :returns: The FPR for this binomial model.

        :examples:

        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
        >>> training_data = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/benign.csv")
        >>> hyper_parameters = {'alpha': [0.01,0.5], 'lambda': [1e-5,1e-6]}
        >>> gs = H2OGridSearch(H2OGeneralizedLinearEstimator(family='binomial'),
        ...                                                  hyper_parameters)
        >>> gs.train(x=[3, 4-11],
        ...          y=3,
        ...          training_frame=training_data)
        >>> gs.fpr(train=True)
        """
        return {model.model_id: model.fpr(thresholds, train, valid, xval) for model in self.models}


    def recall(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the Recall (AKA True Positive Rate) for a set of thresholds.

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: thresholds parameter must be a list (e.g. ``[0.01, 0.5, 0.99]``).
            If None, then the threshold maximizing the metric will be used.
        :param bool train: If train is ``True``, then return the recall value for the training data.
        :param bool valid: If valid is ``True``, then return the recall value for the validation data.
        :param bool xval:  If xval is ``True``, then return the recall value for the cross validation data.
        :returns: The recall for this binomial model.

        :examples:

        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
        >>> training_data = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/benign.csv")
        >>> hyper_parameters = {'alpha': [0.01,0.5], 'lambda': [1e-5,1e-6]}
        >>> gs = H2OGridSearch(H2OGeneralizedLinearEstimator(family='binomial'),
        ...                                                  hyper_parameters)
        >>> gs.train(x=[3, 4-11],
        ...          y=3,
        ...          training_frame=training_data)
        >>> gs.recall(train=True)
        """
        return {model.model_id: model.recall(thresholds, train, valid, xval) for model in self.models}


    def sensitivity(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the sensitivity (AKA True Positive Rate or Recall) for a set of thresholds.

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: thresholds parameter must be a list (e.g. ``[0.01, 0.5, 0.99]``).
            If None, then the threshold maximizing the metric will be used.
        :param bool train: If train is ``True``, then return the sensitivity value for the training data.
        :param bool valid: If valid is ``True``, then return the sensitivity value for the validation data.
        :param bool xval:  If xval is ``True``, then return the sensitivity value for the cross validation data.
        :returns: The sensitivity for this binomial model.

        :examples:

        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
        >>> training_data = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/benign.csv")
        >>> hyper_parameters = {'alpha': [0.01,0.5], 'lambda': [1e-5,1e-6]}
        >>> gs = H2OGridSearch(H2OGeneralizedLinearEstimator(family='binomial'),
        ...                                                  hyper_parameters)
        >>> gs.train(x=[3, 4-11],
        ...          y=3,
        ...          training_frame=training_data)
        >>> gs.sensitivity(train=True)
        """
        return {model.model_id: model.sensitivity(thresholds, train, valid, xval) for model in self.models}


    def fallout(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the Fallout (AKA False Positive Rate) for a set of thresholds.

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: thresholds parameter must be a list (e.g. ``[0.01, 0.5, 0.99]``).
            If None, then the threshold maximizing the metric will be used.
        :param bool train: If train is ``True``, then return the fallout value for the training data.
        :param bool valid: If valid is ``True``, then return the fallout value for the validation data.
        :param bool xval:  If xval is ``True``, then return the fallout value for the cross validation data.
        :returns: The fallout for this binomial model.

        :examples:

        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
        >>> training_data = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/benign.csv")
        >>> hyper_parameters = {'alpha': [0.01,0.5], 'lambda': [1e-5,1e-6]}
        >>> gs = H2OGridSearch(H2OGeneralizedLinearEstimator(family='binomial'),
        ...                                                  hyper_parameters)
        >>> gs.train(x=[3, 4-11],
        ...          y=3,
        ...          training_frame=training_data)
        >>> gs.fallout(train=True)
        """
        return {model.model_id: model.fallout(thresholds, train, valid, xval) for model in self.models}


    def missrate(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the miss rate (AKA False Negative Rate) for a set of thresholds.

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: thresholds parameter must be a list (e.g. ``[0.01, 0.5, 0.99]``).
            If None, then the threshold maximizing the metric will be used.
        :param bool train: If train is ``True``, then return the missrate value for the training data.
        :param bool valid: If valid is ``True``, then return the missrate value for the validation data.
        :param bool xval:  If xval is ``True``, then return the missrate value for the cross validation data.
        :returns: The missrate for this binomial model.

        :examples:

        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
        >>> training_data = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/benign.csv")
        >>> hyper_parameters = {'alpha': [0.01,0.5], 'lambda': [1e-5,1e-6]}
        >>> gs = H2OGridSearch(H2OGeneralizedLinearEstimator(family='binomial'),
        ...                                                  hyper_parameters)
        >>> gs.train(x=[3, 4-11],
        ...          y=3,
        ...          training_frame=training_data)
        >>> gs.missrate(train=True)
        """
        return {model.model_id: model.missrate(thresholds, train, valid, xval) for model in self.models}


    def specificity(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the specificity (AKA True Negative Rate) for a set of thresholds.

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: thresholds parameter must be a list (e.g. ``[0.01, 0.5, 0.99]``).
            If None, then the threshold maximizing the metric will be used.
        :param bool train: If train is ``True``, then return the specificity value for the training data.
        :param bool valid: If valid is ``True``, then return the specificity value for the validation data.
        :param bool xval:  If xval is ``True``, then return the specificity value for the cross validation data.
        :returns: The specificity for this binomial model.

        :examples:

        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
        >>> training_data = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/benign.csv")
        >>> hyper_parameters = {'alpha': [0.01,0.5], 'lambda': [1e-5,1e-6]}
        >>> gs = H2OGridSearch(H2OGeneralizedLinearEstimator(family='binomial'),
        ...                                                  hyper_parameters)
        >>> gs.train(x=[3, 4-11],
        ...          y=3,
        ...          training_frame=training_data)
        >>> gs.specificity(train=True)
        """
        return {model.model_id: model.specificity(thresholds, train, valid, xval) for model in self.models}


    def mcc(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the MCC for a set of thresholds.

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: thresholds parameter must be a list (e.g. ``[0.01, 0.5, 0.99]``).
            If None, then the threshold maximizing the metric will be used.
        :param bool train: If train is ``True``, then return the mcc value for the training data.
        :param bool valid: If valid is ``True``, then return the mcc value for the validation data.
        :param bool xval:  If xval is ``True``, then return the mcc value for the cross validation data.
        :returns: The MCC for this binomial model.

        :examples:

        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
        >>> training_data = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/benign.csv")
        >>> hyper_parameters = {'alpha': [0.01,0.5], 'lambda': [1e-5,1e-6]}
        >>> gs = H2OGridSearch(H2OGeneralizedLinearEstimator(family='binomial'),
        ...                                                  hyper_parameters)
        >>> gs.train(x=[3, 4-11],
        ...          y=3,
        ...          training_frame=training_data)
        >>> gs.mcc(train=True)
        """
        return {model.model_id: model.mcc(thresholds, train, valid, xval) for model in self.models}


    def max_per_class_error(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the max per class error for a set of thresholds.

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: thresholds parameter must be a list (e.g. ``[0.01, 0.5, 0.99]``).
            If None, then the threshold minimizing the error will be used.
        :param bool train: If train is ``True``, then return the ``max_per_class_error`` value for the training data.
        :param bool valid: If valid is ``True``, then return the ``max_per_class_error`` value for the validation data.
        :param bool xval:  If xval is ``True``, then return the ``max_per_class_error`` value for the cross validation data.
        :returns: The max per class error for this binomial model.

        :examples:

        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
        >>> training_data = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/benign.csv")
        >>> hyper_parameters = {'alpha': [0.01,0.5], 'lambda': [1e-5,1e-6]}
        >>> gs = H2OGridSearch(H2OGeneralizedLinearEstimator(family='binomial'),
        ...                                                  hyper_parameters)
        >>> gs.train(x=[3, 4-11],
        ...          y=3,
        ...          training_frame=training_data)
        >>> gs.max_per_class_error(train=True)
        """
        return {model.model_id: model.max_per_class_error(thresholds, train, valid, xval) for model in self.models}


    def mean_per_class_error(self, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the mean per class error for a set of thresholds.

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param thresholds: thresholds parameter must be a list (e.g. ``[0.01, 0.5, 0.99]``).
            If None, then the threshold minimizing the error will be used.
        :param bool train: If train is ``True``, then return the ``mean_per_class_error`` value for the training data.
        :param bool valid: If valid is ``True``, then return the ``mean_per_class_error`` value for the validation data.
        :param bool xval:  If xval is ``True``, then return the ``mean_per_class_error`` value for the cross validation data.
        :returns: The mean per class error for this binomial model.

        :examples:

        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
        >>> training_data = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/benign.csv")
        >>> hyper_parameters = {'alpha': [0.01,0.5], 'lambda': [1e-5,1e-6]}
        >>> gs = H2OGridSearch(H2OGeneralizedLinearEstimator(family='binomial'),
        ...                                                  hyper_parameters)
        >>> gs.train(x=[3, 4-11],
        ...          y=3,
        ...          training_frame=training_data)
        >>> gs.mean_per_class_error(train=True)
        """
        return {model.model_id: model.mean_per_class_error(thresholds, train, valid, xval) for model in self.models}


    def metric(self, metric, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the metric value for a set of thresholds.

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param metric: name of the metric to compute.
        :param thresholds: thresholds parameter must be a list (e.g. ``[0.01, 0.5, 0.99]``).
            If None, then the threshold maximizing the metric will be used.
        :param bool train: If train is ``True``, then return the metrics for the training data.
        :param bool valid: If valid is ``True``, then return the metrics for the validation data.
        :param bool xval:  If xval is ``True``, then return the metrics for the cross validation data.
        :returns: The metrics for this binomial model.

        :examples:

        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
        >>> training_data = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/benign.csv")
        >>> hyper_parameters = {'alpha': [0.01,0.5], 'lambda': [1e-5,1e-6]}
        >>> gs = H2OGridSearch(H2OGeneralizedLinearEstimator(family='binomial'),
        ...                                                  hyper_parameters)
        >>> gs.train(x=[3, 4-11],
        ...          y=3,
        ...          training_frame=training_data)
        >>> gs.metric("tps", train=True)
        """
        return {model.model_id: model.metric(metric, thresholds, train, valid, xval) for model in self.models}


    def roc(self, train=False, valid=False, xval=False):
        """
        Return the coordinates of the ROC curve for a given set of data, as a two-tuple containing the false positive
        rates as a list and true positive rates as a list.

        If all are ``False`` (default), then return  the training data.
        If more than one ROC curve is requested, the data is returned as a dictionary of two-tuples.

        :param bool train: If train is ``True``, then return the ROC coordinates for the training data.
        :param bool valid: If valid is ``True``, then return the ROC coordinates for the validation data.
        :param bool xval: If xval is ``True``, then return the ROC coordinates for the cross validation data.
        :returns: the true cooridinates of the roc curve.

        :examples:

        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
        >>> training_data = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/benign.csv")
        >>> hyper_parameters = {'alpha': [0.01,0.5], 'lambda': [1e-5,1e-6]}
        >>> gs = H2OGridSearch(H2OGeneralizedLinearEstimator(family='binomial'),
        ...                                                  hyper_parameters)
        >>> gs.train(x=[3, 4-11],
        ...          y=3,
        ...          training_frame=training_data)
        >>> gs.roc(train=True)
        """
        return {model.model_id: model.roc(train, valid, xval) for model in self.models}


    def confusion_matrix(self, metrics=None, thresholds=None, train=False, valid=False, xval=False):
        """
        Get the confusion matrix for the specified metrics/thresholds.

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param metrics: A string (or list of strings) among metrics listed in :const:`H2OBinomialModelMetrics.maximizing_metrics`.
            Defaults to ``'f1'``.
        :param thresholds: A value (or list of values) between 0 and 1.
            If None, then the thresholds maximizing each provided metric will be used.
        :param bool train: If train is ``True``, then return the confusion matrix value for the training data.
        :param bool valid: If valid is ``True``, then return the confusion matrix value for the validation data.
        :param bool xval:  If xval is ``True``, then return the confusion matrix value for the cross validation data.
        :returns: The confusion matrix for this binomial model.

        :examples:

        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
        >>> training_data = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/benign.csv")
        >>> hyper_parameters = {'alpha': [0.01,0.5], 'lambda': [1e-5,1e-6]}
        >>> gs = H2OGridSearch(H2OGeneralizedLinearEstimator(family='binomial'),
        ...                                                  hyper_parameters)
        >>> gs.train(x=[3, 4-11],
        ...          y=3,
        ...          training_frame=training_data)
        >>> gs.confusion_matrix(train=True)
        """
        return {model.model_id: model.confusion_matrix(metrics, thresholds, train, valid, xval) for model in
                self.models}


    def find_threshold_by_max_metric(self, metric, train=False, valid=False, xval=False):
        """
        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param str metric: A metric among the metrics listed in :const:`H2OBinomialModelMetrics.maximizing_metrics`.
        :param bool train: If train is ``True``, then return the ``threshold_by_max_metric`` value for the training data.
        :param bool valid: If valid is ``True``, then return the ``threshold_by_max_metric`` value for the validation data.
        :param bool xval:  If xval is ``True``, then return the ``threshold_by_max_metric`` value for the cross validation data.
        :returns: The ``threshold_by_max_metric`` for this binomial model.

        :examples:

        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
        >>> training_data = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/benign.csv")
        >>> hyper_parameters = {'alpha': [0.01,0.5], 'lambda': [1e-5,1e-6]}
        >>> gs = H2OGridSearch(H2OGeneralizedLinearEstimator(family='binomial'),
        ...                                                  hyper_parameters)
        >>> gs.train(x=[3, 4-11],
        ...          y=3,
        ...          training_frame=training_data)
        >>> gs.find_threshold_by_max_metric("tps", train=True)
        
        """
        return {model.model_id: model.find_threshold_by_max_metric(metric, train, valid, xval) for model in self.models}


    def find_idx_by_threshold(self, threshold, train=False, valid=False, xval=False):
        """
        Retrieve the index in this metric's threshold list at which the given threshold is located.

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param float threshold: The threshold value to search for.
        :param bool train: If train is ``True``, then return the ``idx_by_threshold`` for the training data.
        :param bool valid: If valid is ``True``, then return the ``idx_by_threshold`` for the validation data.
        :param bool xval:  If xval is ``True``, then return the ``idx_by_threshold`` for the cross validation data.
        :returns: The ``idx_by_threshold`` for this binomial model.

        :examples:

        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
        >>> training_data = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/benign.csv")
        >>> hyper_parameters = {'alpha': [0.01,0.5], 'lambda': [1e-5,1e-6]}
        >>> gs = H2OGridSearch(H2OGeneralizedLinearEstimator(family='binomial'),
        ...                                                  hyper_parameters)
        >>> gs.train(x=[3, 4-11],
        ...          y=3,
        ...          training_frame=training_data)
        >>> gs.find_idx_by_threshold(0.45, train=True)
        """
        return {model.model_id: model.find_idx_by_threshold(threshold, train, valid, xval) for model in self.models}




#-----------------------------------------------------------------------------------------------------------------------
# Clustering Grid Search
#-----------------------------------------------------------------------------------------------------------------------

class H2OClusteringGridSearch(object):

    def size(self, train=False, valid=False, xval=False):
        """
        Get the sizes of each cluster.

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where
        the keys are "train", "valid", and "xval".

        :param bool train: If ``True``, then return the cluster sizes for the training data.
        :param bool valid: If ``True``, then return the cluster sizes for the validation data.
        :param bool xval: If ``True``, then return the cluster sizes for each of the cross-validated splits.
        :returns: the cluster sizes for the specified key(s).

        :examples:

        >>> from h2o.estimators import H2OKMeansEstimator
        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> iris = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/iris/iris_train.csv")
        >>> hyper_parameters = {'k': [2,3,4], 'init': "random"}
        >>> gs = H2OGridSearch(H2OKMeansEstimator(), hyper_parameters)
        >>> gs.train(x=list(range(4)), training_frame=iris)
        >>> gs.size(train=True)
        """
        return {model.model_id: model.size(train, valid, xval) for model in self.models}


    def num_iterations(self):
        """Get the number of iterations that it took to converge or reach max iterations.

        :examples:

        >>> from h2o.estimators import H2OKMeansEstimator
        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> iris = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/iris/iris_train.csv")
        >>> hyper_parameters = {'k': [2,3,4], 'init': "random"}
        >>> gs = H2OGridSearch(H2OKMeansEstimator(), hyper_parameters)
        >>> gs.train(x=list(range(4)), training_frame=iris)
        >>> gs.num_iterations()
        """
        
        return {model.model_id: model.num_iterations() for model in self.models}


    def betweenss(self, train=False, valid=False, xval=False):
        """
        Get the between cluster sum of squares.

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where
        the keys are "train", "valid", and "xval".

        :param bool train: If ``True``, then return the between cluster sum of squares value for the training data.
        :param bool valid: If ``True``, then return the between cluster sum of squares value for the validation data.
        :param bool xval: If ``True``, then return the between cluster sum of squares value for each of the
            cross-validated splits.
        :returns: the between cluster sum of squares values for the specified key(s).

        :examples:

        >>> from h2o.estimators import H2OKMeansEstimator
        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> iris = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/iris/iris_train.csv")
        >>> hyper_parameters = {'k': [2,3,4], 'init': "random"}
        >>> gs = H2OGridSearch(H2OKMeansEstimator(), hyper_parameters)
        >>> gs.train(x=list(range(4)), training_frame=iris)
        >>> gs.betweenss(train=True)
        """
        return {model.model_id: model.betweenss(train, valid, xval) for model in self.models}


    def totss(self, train=False, valid=False, xval=False):
        """
        Get the total sum of squares.

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where
        the keys are "train", "valid", and "xval".

        :param bool train: If ``True``, then return total sum of squares for the training data.
        :param bool valid: If ``True``, then return the total sum of squares for the validation data.
        :param bool xval: If ``True``, then return the total sum of squares for each of the cross-validated splits.
        :returns: the total sum of squares values for the specified key(s).

        :examples:

        >>> from h2o.estimators import H2OKMeansEstimator
        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> iris = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/iris/iris_train.csv")
        >>> hyper_parameters = {'k': [2,3,4], 'init': "random"}
        >>> gs = H2OGridSearch(H2OKMeansEstimator(), hyper_parameters)
        >>> gs.train(x=list(range(4)), training_frame=iris)
        >>> gs.totss(train=True)
        """
        return {model.model_id: model.totss(train, valid, xval) for model in self.models}


    def tot_withinss(self, train=False, valid=False, xval=False):
        """
        Get the total within cluster sum of squares.

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where
        the keys are "train", "valid", and "xval".

        :param bool train: If ``True``, then return the total within cluster sum of squares for the training data.
        :param bool valid: If ``True``, then return the total within cluster sum of squares for the validation data.
        :param bool xval: If ``True``, then return the total within cluster sum of squares for each of the
            cross-validated splits.
        :returns: the total within cluster sum of squares values for the specified key(s).

        :examples:

        >>> from h2o.estimators import H2OKMeansEstimator
        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> iris = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/iris/iris_train.csv")
        >>> hyper_parameters = {'k': [2,3,4], 'init': "random"}
        >>> gs = H2OGridSearch(H2OKMeansEstimator(), hyper_parameters)
        >>> gs.train(x=list(range(4)), training_frame=iris)
        >>> gs.tot_withinss(train=True)
        """
        return {model.model_id: model.tot_withinss(train, valid, xval) for model in self.models}


    def withinss(self, train=False, valid=False, xval=False):
        """
        Get the within cluster sum of squares for each cluster.

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where
        the keys are "train", "valid", and "xval".

        :param bool train: If ``True``, then return within cluster sum of squares for the training data.
        :param bool valid: If ``True``, then return the within cluster sum of squares for the validation data.
        :param bool xval: If ``True``, then return the within cluster sum of squares for each of the
            cross-validated splits.
        :returns: the within cluster sum of squares values for the specified key(s).

        :examples:

        >>> from h2o.estimators import H2OKMeansEstimator
        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> iris = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/iris/iris_train.csv")
        >>> hyper_parameters = {'k': [2,3,4], 'init': "random"}
        >>> gs = H2OGridSearch(H2OKMeansEstimator(), hyper_parameters)
        >>> gs.train(x=list(range(4)), training_frame=iris)
        >>> gs.withinss(train=True)
        """
        return {model.model_id: model.withinss(train, valid, xval) for model in self.models}


    def centroid_stats(self, train=False, valid=False, xval=False):
        """
        Get the centroid statistics for each cluster.

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where
        the keys are "train", "valid", and "xval".

        :param bool train: If ``True``, then return the centroid statistics for the training data.
        :param bool valid: If ``True``, then return the centroid statistics for the validation data.
        :param bool xval: If ``True``, then return the centroid statistics for each of the cross-validated splits.
        :returns: the centroid statistics for the specified key(s).

        :examples:

        >>> from h2o.estimators import H2OKMeansEstimator
        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> iris = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/iris/iris_train.csv")
        >>> hyper_parameters = {'k': [2,3,4], 'init': "random"}
        >>> gs = H2OGridSearch(H2OKMeansEstimator(), hyper_parameters)
        >>> gs.train(x=list(range(4)), training_frame=iris)
        >>> gs.centroid_stats(train=True)
        """
        return {model.model_id: model.centroid_stats(train, valid, xval) for model in self.models}


    def centers(self):
        """Returns the centers for the KMeans model.

        :examples:

        >>> from h2o.estimators import H2OKMeansEstimator
        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> iris = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/iris/iris_train.csv")
        >>> hyper_parameters = {'k': [2,3,4], 'init': "random"}
        >>> gs = H2OGridSearch(H2OKMeansEstimator(), hyper_parameters)
        >>> gs.train(x=list(range(4)), training_frame=iris)
        >>> gs.centers()
        """
        return {model.model_id: model.centers() for model in self.models}


    def centers_std(self):
        """Returns the standardized centers for the KMeans model.

        :examples:

        >>> from h2o.estimators import H2OKMeansEstimator
        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> iris = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/iris/iris_train.csv")
        >>> hyper_parameters = {'k': [2,3,4], 'init': "random"}
        >>> gs = H2OGridSearch(H2OKMeansEstimator(), hyper_parameters)
        >>> gs.train(x=list(range(4)), training_frame=iris)
        >>> gs.centers_std()
        """
        return {model.model_id: model.centers_std() for model in self.models}




#-----------------------------------------------------------------------------------------------------------------------
# Dimensionality Reduction Grid Search
#-----------------------------------------------------------------------------------------------------------------------

class H2ODimReductionGridSearch(object):
    def num_iterations(self):
        """
        Get the number of iterations that it took to converge or reach max iterations.

        :returns: number of iterations (integer).

        :examples:

        >>> from h2o.estimators import H2OGeneralizedLowRankEstimator
        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
        >>> hyper_parameters = {'gamma_x': [0.05, 0.5], 'gamma_y': [0.05,0.5]}
        >>> gs = H2OGridSearch(H2OGeneralizedLowRankEstimator(),
        ...                    hyper_parameters)
        >>> gs.train(x=iris.names, training_frame=iris)
        >>> gs.num_iterations()
        """
        return {model.model_id: model.num_iterations for model in self.models}

    def objective(self):
        """
        Get the final value of the objective function from the GLRM model.

        :returns: final objective value (double).

        :examples:

        >>> from h2o.estimators import H2OGeneralizedLowRankEstimator
        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
        >>> hyper_parameters = {'gamma_x': [0.05, 0.5], 'gamma_y': [0.05,0.5]}
        >>> gs = H2OGridSearch(H2OGeneralizedLowRankEstimator(),
        ...                    hyper_parameters)
        >>> gs.train(x=iris.names, training_frame=iris)
        >>> gs.objective()
        """
        return {model.model_id: model.objective for model in self.models}

    def final_step(self):
        """
        Get the final step size from the GLRM model.

        :returns: final step size (double).

        :examples:

        >>> from h2o.estimators import H2OGeneralizedLowRankEstimator
        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
        >>> hyper_parameters = {'gamma_x': [0.05, 0.5], 'gamma_y': [0.05,0.5]}
        >>> gs = H2OGridSearch(H2OGeneralizedLowRankEstimator(),
        ...                    hyper_parameters)
        >>> gs.train(x=iris.names, training_frame=iris)
        >>> gs.final_step()
        """
        return {model.model_id: model.final_step for model in self.models}

    def archetypes(self):
        """
        :returns: the archetypes (Y) of the GLRM model.

        :examples:

        >>> from h2o.estimators import H2OGeneralizedLowRankEstimator
        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
        >>> hyper_parameters = {'gamma_x': [0.05, 0.5], 'gamma_y': [0.05,0.5]}
        >>> gs = H2OGridSearch(H2OGeneralizedLowRankEstimator(),
        ...                    hyper_parameters)
        >>> gs.train(x=iris.names, training_frame=iris)
        >>> gs.archetypes()
        """
        return {model.model_id: model.archetypes for model in self.models}




#-----------------------------------------------------------------------------------------------------------------------
# Multinomial Grid Search
#-----------------------------------------------------------------------------------------------------------------------

class H2OMultinomialGridSearch(object):
    def confusion_matrix(self, data):
        """
        Returns a confusion matrix based of H2O's default prediction threshold for a dataset.

        :param data: metric for which the confusion matrix will be calculated.

        :examples:

        >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris.csv")
        >>> hyper_parameters = {'alpha': [0.01,0.5], 'lambda': [1e-5,1e-6]}
        >>> gs = H2OGridSearch(H2OGeneralizedLinearEstimator(family = "multinomial"),
        ...                                                  hyper_parameters)
        >>> gs.train(x=[0,1,2,3], y=4, training_frame=iris)
        >>> gs.confusion_matrix(iris)
        """
        return {model.model_id: model.confusion_matrix(data) for model in self.models}


    def hit_ratio_table(self, train=False, valid=False, xval=False):
        """
        Retrieve the Hit Ratios.

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param bool train: If train is ``True``, then return the hit ratio value for the training data.
        :param bool valid: If valid is ``True``, then return the hit ratio value for the validation data.
        :param bool xval:  If xval is ``True``, then return the hit ratio value for the cross validation data.
        :returns: The hit ratio for this multinomial model.

        :examples:

        >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> hyper_parameters = {'alpha': [0.01,0.5], 'lambda': [1e-5,1e-6]}
        >>> iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris.csv")
        >>> gs = H2OGridSearch(H2OGeneralizedLinearEstimator(family = "multinomial"),
        ...                                                  hyper_parameters)
        >>> gs.train(x=[0,1,2,3], y=4, training_frame=iris)
        >>> gs.hit_ratio_table(train=True)
        """
        return {model.model_id: model.hit_ratio_table(train, valid, xval) for model in self.models}

    def auc(self, train=False, valid=False, xval=False):
        """
        Retrieve the AUC value.

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param bool train: If train is ``True``, then return the AUC values for the training data.
        :param bool valid: If valid is ``True``, then return the AUC values for the validation data.
        :param bool xval:  If xval is ``True``, then return the AUC values for the cross validation data.
        :returns: The AUC values for this multinomial model.

        :examples:

        >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> hyper_parameters = {'alpha': [0.01,0.5], 'lambda': [1e-5,1e-6]}
        >>> iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris.csv")
        >>> gs = H2OGridSearch(H2OGeneralizedLinearEstimator(family = "multinomial"),
        ...                                                  hyper_parameters)
        >>> gs.train(x=[0,1,2,3], y=4, training_frame=iris)
        >>> gs.auc(train=True)
        """
        return {model.model_id: model.auc(train, valid, xval) for model in self.models}

    def aucpr(self, train=False, valid=False, xval=False):
        """
        Retrieve the PR AUC value.

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param bool train: If train is ``True``, then return the PR AUC values for the training data.
        :param bool valid: If valid is ``True``, then return the PR AUC values for the validation data.
        :param bool xval:  If xval is ``True``, then return the PR AUC values for the cross validation data.
        :returns: The PR AUC values for this multinomial model.

        :examples:

        >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> hyper_parameters = {'alpha': [0.01,0.5], 'lambda': [1e-5,1e-6]}
        >>> iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris.csv")
        >>> gs = H2OGridSearch(H2OGeneralizedLinearEstimator(family = "multinomial"),
        ...                                                  hyper_parameters)
        >>> gs.train(x=[0,1,2,3], y=4, training_frame=iris)
        >>> gs.aucpr(train=True)
        """
        return {model.model_id: model.aucpr(train, valid, xval) for model in self.models}

    def mean_per_class_error(self, train=False, valid=False, xval=False):
        """
        Get the mean per class error.

        If all are ``False`` (default), then return the training metric value.
        If more than one options is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param bool train: If train is ``True``, then return the mean per class error value for the training data.
        :param bool valid: If valid is ``True``, then return the mean per class error value for the validation data.
        :param bool xval:  If xval is ``True``, then return the mean per class error value for the cross validation data.
        :returns: The mean per class error for this multinomial model.

        :examples:

        >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> hyper_parameters = {'alpha': [0.01,0.5], 'lambda': [1e-5,1e-6]}
        >>> iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris.csv")
        >>> gs = H2OGridSearch(H2OGeneralizedLinearEstimator(family = "multinomial"),
        ...                                                  hyper_parameters)
        >>> gs.train(x=[0,1,2,3], y=4, training_frame=iris)
        >>> gs.mean_per_class_error(train=True)
        """
        return {model.model_id: model.mean_per_class_error(train, valid, xval) for model in self.models}


#-----------------------------------------------------------------------------------------------------------------------
# Ordinal Grid Search
#-----------------------------------------------------------------------------------------------------------------------

class H2OOrdinalGridSearch(object):
    def confusion_matrix(self, data):
        """
        Returns a confusion matrix based of H2O's default prediction threshold for a dataset.

        :param data: metric for which the confusion matrix will be calculated.

        :examples:

        >>> from h2o.estimators import H2OGeneralizedLinearEstimator
        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> h2o_df = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/bigdata/laptop/glm_ordinal_logit/ordinal_multinomial_training_set.csv")
        >>> hyper_parameters = {'alpha': [0.01,0.5], 'lambda': [1e-5,1e-6]}
        >>> gs = H2OGridSearch(H2OGeneralizedLinearEstimator(family="ordinal"), hyper_parameters)
        >>> h2o_df['C11'] = h2o_df['C11'].asfactor()
        >>> gs.train(x=list(range(0,10)), y="C11", training_frame=h2o_df)
        >>> gs.confusion_matrix(h2o_df)
        """
        return {model.model_id: model.confusion_matrix(data) for model in self.models}


    def hit_ratio_table(self, train=False, valid=False, xval=False):
        """
        Retrieve the Hit Ratios.

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param bool train: If train is ``True``, then return the hit ratio value for the training data.
        :param bool valid: If valid is ``True``, then return the hit ratio value for the validation data.
        :param bool xval:  If xval is ``True``, then return the hit ratio value for the cross validation data.
        :returns: The hit ratio for this ordinal model.

        :examples:

        >>> from h2o.estimators import H2OGeneralizedLinearEstimator
        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> h2o_df = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/bigdata/laptop/glm_ordinal_logit/ordinal_multinomial_training_set.csv")
        >>> hyper_parameters = {'alpha': [0.01,0.5], 'lambda': [1e-5,1e-6]}
        >>> gs = H2OGridSearch(H2OGeneralizedLinearEstimator(family="ordinal"), hyper_parameters)
        >>> h2o_df['C11'] = h2o_df['C11'].asfactor()
        >>> gs.train(x=list(range(0,10)), y="C11", training_frame=h2o_df)
        >>> gs.hit_ratio_table(train=True)
        """
        return {model.model_id: model.hit_ratio_table(train, valid, xval) for model in self.models}


    def mean_per_class_error(self, train=False, valid=False, xval=False):
        """
        Get the mean per class error.

        If all are ``False`` (default), then return the training metric value.
        If more than one options is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param bool train: If train is ``True``, then return the mean per class error value for the training data.
        :param bool valid: If valid is ``True``, then return the mean per class error value for the validation data.
        :param bool xval:  If xval is ``True``, then return the mean per class error value for the cross validation data.
        :returns: The mean per class error for this ordinal model.

        :examples:

        >>> from h2o.estimators import H2OGeneralizedLinearEstimator
        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> h2o_df = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/bigdata/laptop/glm_ordinal_logit/ordinal_multinomial_training_set.csv")
        >>> hyper_parameters = {'alpha': [0.01,0.5], 'lambda': [1e-5,1e-6]}                          
        >>> gs = H2OGridSearch(H2OGeneralizedLinearEstimator(family="ordinal"), hyper_parameters)
        >>> h2o_df['C11'] = h2o_df['C11'].asfactor()
        >>> gs.train(x=list(range(0,10)), y="C11", training_frame=h2o_df)
        >>> gs.mean_per_class_error(train=True)
        """
        return {model.model_id: model.mean_per_class_error(train, valid, xval) for model in self.models}

#-----------------------------------------------------------------------------------------------------------------------
# Regression Grid Search
#-----------------------------------------------------------------------------------------------------------------------

class H2ORegressionGridSearch(object):
    pass
