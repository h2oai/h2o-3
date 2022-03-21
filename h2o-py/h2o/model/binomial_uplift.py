# -*- encoding: utf-8 -*-
from __future__ import absolute_import, division, print_function, unicode_literals
from h2o.utils.compatibility import *  # NOQA

from h2o.exceptions import H2OValueError
from h2o.utils.typechecks import assert_is_type
from .model_base import ModelBase


class H2OBinomialUpliftModel(ModelBase):

    def auuc(self, train=False, valid=False, metric=None):
        """
        Retrieve area under uplift curve (AUUC) value for the specified metrics in model params.
        
        If all are False (default), then return the training metric AUUC value.
        If more than one options is set to True, then return a dictionary of metrics where the 
        keys are "train" and "valid".
        
        :param bool train: If True, return the AUUC value for the training data.
        :param bool valid: If True, return the AUUC value for the validation data.
        :param metric: AUUC metric type ("qini", "lift", "gain", default is None which means metric set in parameters) 
    
        
        :returns: AUUC value for the specified key(s).

        :examples:
        
        >>> from h2o.estimators import H2OUpliftRandomForestEstimator
        >>> train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/uplift/criteo_uplift_13k.csv")
        >>> treatment_column = "treatment"
        >>> response_column = "conversion"
        >>> train[treatment_column] = train[treatment_column].asfactor()
        >>> train[response_column] = train[response_column].asfactor()
        >>> predictors = ["f1", "f2", "f3", "f4", "f5", "f6"]
        >>>
        >>> uplift_model = H2OUpliftRandomForestEstimator(ntrees=10, 
        ...                                               max_depth=5,
        ...                                               treatment_column=treatment_column,
        ...                                               uplift_metric="qini",
        ...                                               distribution="bernoulli",
        ...                                               gainslift_bins=10,
        ...                                               min_rows=10,
        ...                                               auuc_type="gain")
        >>> uplift_model.train(y=response_column, x=predictors, training_frame=train)
        >>> uplift_model.auuc() # <- Default: return training metric value
        >>> uplift_model.auuc(train=True,  valid=True)
        """
        assert metric in [None, 'qini', 'lift', 'gain'], "AUUC metric "+metric+" should be None, 'qini','lift' or 'gain'."
        return self._delegate_to_metrics(metric=metric, method='auuc', train=train, valid=valid)

    def uplift(self, train=False, valid=False, metric="qini"):
        """
        Retrieve uplift values for the specified metrics. 
        
        If all are False (default), then return the training metric uplift values.
        If more than one options is set to True, then return a dictionary of metrics where the 
        keys are "train" and "valid".
        
        :param bool train: If True, return the uplift values for the training data.
        :param bool valid: If True, return the uplift values for the validation data.
        :param metric: Uplift metric type ("qini", "lift", "gain", default is "qini") 
        
        :returns: a list of uplift values for the specified key(s).

        :examples:
        
        >>> from h2o.estimators import H2OUpliftRandomForestEstimator
        >>> train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/uplift/criteo_uplift_13k.csv")
        >>> treatment_column = "treatment"
        >>> response_column = "conversion"
        >>> train[treatment_column] = train[treatment_column].asfactor()
        >>> train[response_column] = train[response_column].asfactor()
        >>> predictors = ["f1", "f2", "f3", "f4", "f5", "f6"]
        >>>
        >>> uplift_model = H2OUpliftRandomForestEstimator(ntrees=10, 
        ...                                               max_depth=5,
        ...                                               treatment_column=treatment_column,
        ...                                               uplift_metric="qini",
        ...                                               distribution="bernoulli",
        ...                                               gainslift_bins=10,
        ...                                               min_rows=10,
        ...                                               auuc_type="gain")
        >>> uplift_model.train(y=response_column, x=predictors, training_frame=train)
        >>> uplift_model.uplift() # <- Default: return training metric value
        >>> uplift_model.uplift(train=True, metric="gain")
        """
        assert metric in ['qini', 'lift', 'gain'], "Uplift metric "+metric+" should be 'qini','lift' or 'gain'."
        return self._delegate_to_metrics(metric, method='uplift', train=train, valid=valid)

    def n(self, train=False, valid=False):
        """
        Retrieve numbers of observations.
        
        If all are False (default), then return the training metric number of observations.
        If more than one options is set to True, then return a dictionary of metrics where the 
        keys are "train" and "valid".
        
        :param bool train: If True, return the number of observations for the training data.
        :param bool valid: If True, return the number of observations for the validation data.
        
        :returns: a list of numbers of observation for the specified key(s).

        :examples:
        
        >>> from h2o.estimators import H2OUpliftRandomForestEstimator
        >>> train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/uplift/criteo_uplift_13k.csv")
        >>> treatment_column = "treatment"
        >>> response_column = "conversion"
        >>> train[treatment_column] = train[treatment_column].asfactor()
        >>> train[response_column] = train[response_column].asfactor()
        >>> predictors = ["f1", "f2", "f3", "f4", "f5", "f6"]
        >>>
        >>> uplift_model = H2OUpliftRandomForestEstimator(ntrees=10, 
        ...                                               max_depth=5,
        ...                                               treatment_column=treatment_column,
        ...                                               uplift_metric="qini",
        ...                                               distribution="bernoulli",
        ...                                               gainslift_bins=10,
        ...                                               min_rows=10,
        ...                                               auuc_type="gain")
        >>> uplift_model.train(y=response_column, x=predictors, training_frame=train)
        >>> uplift_model.n()  # <- Default: return training metric value
        >>> uplift_model.n(train=True)
        """
        return self._delegate_to_metrics(method='n', train=train, valid=valid)
    
    def thresholds(self, train=False, valid=False):
        """
        Retrieve prediction thresholds for the specified metrics. 
        
        If all are False (default), then return the training metric prediction thresholds.
        If more than one options is set to True, then return a dictionary of metrics where the 
        keys are "train" and "valid".
        
        :param bool train: If True, return the prediction thresholds for the training data.
        :param bool valid: If True, return the prediction thresholds for the validation data.
        
        :returns: a list of numbers of observation for the specified key(s).
        
        :examples:
        
        >>> from h2o.estimators import H2OUpliftRandomForestEstimator
        >>> train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/uplift/criteo_uplift_13k.csv")
        >>> treatment_column = "treatment"
        >>> response_column = "conversion"
        >>> train[treatment_column] = train[treatment_column].asfactor()
        >>> train[response_column] = train[response_column].asfactor()
        >>> predictors = ["f1", "f2", "f3", "f4", "f5", "f6"]
        >>>
        >>> uplift_model = H2OUpliftRandomForestEstimator(ntrees=10, 
        ...                                               max_depth=5,
        ...                                               treatment_column=treatment_column,
        ...                                               uplift_metric="qini",
        ...                                               distribution="bernoulli",
        ...                                               gainslift_bins=10,
        ...                                               min_rows=10,
        ...                                               auuc_type="gain")
        >>> uplift_model.train(y=response_column, x=predictors, training_frame=train)
        >>> uplift_model.thresholds()  # <- Default: return training metric value
        >>> uplift_model.thresholds(train=True)
        """
        return self._delegate_to_metrics(method='thresholds', train=train, valid=valid, xval=False)

    def thresholds_and_metric_scores(self, train=False, valid=False):
        """
        Retrieve thresholds and metric scores table for the specified metrics. 
        
        If all are False (default), then return the training metric thresholds and metric scores table.
        If more than one options is set to True, then return a dictionary of metrics where the 
        keys are "train" and "valid".
        
        :param bool train: If True, return the thresholds and metric scores table for the training data.
        :param bool valid: If True, return the thresholds and metric scores table for the validation data.
        
        :returns: the thresholds and metric scores table for the specified key(s).
        
        :examples:
        
        >>> from h2o.estimators import H2OUpliftRandomForestEstimator
        >>> train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/uplift/criteo_uplift_13k.csv")
        >>> treatment_column = "treatment"
        >>> response_column = "conversion"
        >>> train[treatment_column] = train[treatment_column].asfactor()
        >>> train[response_column] = train[response_column].asfactor()
        >>> predictors = ["f1", "f2", "f3", "f4", "f5", "f6"]
        >>>
        >>> uplift_model = H2OUpliftRandomForestEstimator(ntrees=10, 
        ...                                               max_depth=5,
        ...                                               treatment_column=treatment_column,
        ...                                               uplift_metric="qini",
        ...                                               distribution="bernoulli",
        ...                                               gainslift_bins=10,
        ...                                               min_rows=10,
        ...                                               auuc_type="gain")
        >>> uplift_model.train(y=response_column, x=predictors, training_frame=train)
        >>> uplift_model.thresholds_and_metric_scores()  # <- Default: return training metric value
        >>> uplift_model.thresholds_and_metric_scores(train=True)
        """
        return self._delegate_to_metrics(method='thresholds_and_metric_scores', train=train, valid=valid, xval=False)

    def auuc_table(self, train=False, valid=False):
        """
        Retrieve all types of AUUC in a table.
        
        If all are False (default), then return the training metric AUUC table.
        If more than one options is set to True, then return a dictionary of metrics where the 
        keys are "train" and "valid".
        
        :param bool train: If True, return the AUUC table for the training data.
        :param bool valid: If True, return the AUUC table for the validation data.
         
        :returns: the AUUC table for the specified key(s).
    
        :examples:
         
        >>> from h2o.estimators import H2OUpliftRandomForestEstimator
        >>> train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/uplift/criteo_uplift_13k.csv")
        >>> treatment_column = "treatment"
        >>> response_column = "conversion"
        >>> train[treatment_column] = train[treatment_column].asfactor()
        >>> train[response_column] = train[response_column].asfactor()
        >>> predictors = ["f1", "f2", "f3", "f4", "f5", "f6"]
        >>>
        >>> uplift_model = H2OUpliftRandomForestEstimator(ntrees=10, 
        ...                                               max_depth=5,
        ...                                               treatment_column=treatment_column,
        ...                                               uplift_metric="qini",
        ...                                               distribution="bernoulli",
        ...                                               gainslift_bins=10,
        ...                                               min_rows=10,
        ...                                               auuc_type="gain")
        >>> uplift_model.train(y=response_column, x=predictors, training_frame=train)
        >>> uplift_model.auuc_table() # <- Default: return training metric value
        >>> uplift_model.auuc_table(train=True)
        """
        return self._delegate_to_metrics(method='auuc_table', train=train, valid=valid)

    def _delegate_to_metrics(self, method, train=False, valid=False, **kwargs):
        tm = ModelBase._get_metrics(self, train, valid, xval=None, )
        m = {}
        for k, v in viewitems(tm):
            if v is None:
                m[k] = None
            elif hasattr(v, method) and callable(getattr(v, method)):
                m[k] = getattr(v, method)(**kwargs)
            else:
                raise ValueError('no method {} in {}'.format(method, type(v)))
        return list(m.values())[0] if len(m) == 1 else m
