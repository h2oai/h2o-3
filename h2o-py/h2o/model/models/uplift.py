# -*- encoding: utf-8 -*-
from __future__ import absolute_import, division, print_function, unicode_literals
from h2o.utils.compatibility import *  # NOQA

from h2o.model import ModelBase
from h2o.utils.metaclass import deprecated_params_order

_old_sig = ['train', 'valid', 'metric']
def _is_called_with_old_sig(*args, **kwargs): return len(args) > 0 and isinstance(args[0], bool)


class H2OBinomialUpliftModel(ModelBase):

    @deprecated_params_order(old_sig=_old_sig, is_called_with_old_sig=_is_called_with_old_sig)
    def auuc(self, metric=None, train=False, valid=False):
        """
        Retrieve area under uplift curve (AUUC) value for the specified metrics in model params.
        
        If all are ``False`` (default), then return the training metric AUUC value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the 
        keys are "train" and "valid".

        :param bool train: If ``True``, return the AUUC value for the training data.
        :param bool valid: If ``True``, return the AUUC value for the validation data.
        :param metric: AUUC metric type. One of:

            - "qini"
            - "lift"
            - "gain"
            - "None" (default; metric set in parameters)     
        
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
        ...                                               uplift_metric="kl",
        ...                                               distribution="bernoulli",
        ...                                               min_rows=10,
        ...                                               auuc_type="gain")
        >>> uplift_model.train(y=response_column, x=predictors, training_frame=train)
        >>> uplift_model.auuc() # <- Default: return training metric value
        >>> uplift_model.auuc(train=True,  valid=True)
        """
        assert metric in [None, 'qini', 'lift', 'gain'], "AUUC metric "+metric+" should be None, 'qini','lift' or 'gain'."
        return self._delegate_to_metrics(metric=metric, method='auuc', train=train, valid=valid)

    @deprecated_params_order(old_sig=_old_sig, is_called_with_old_sig=_is_called_with_old_sig)
    def auuc_normalized(self, metric=None, train=False, valid=False):
        """
        Retrieve normalized area under uplift curve (AUUC) value for the specified metrics in model params.

        If all are ``False`` (default), then return the training metric normalized AUUC value.
        If more than one options is set to ``True``, then return a dictionary of metrics where the 
        keys are "train" and "valid".

        :param metric: AUUC metric type ("qini", "lift", "gain", default is None which means metric set in parameters)
        :param bool train: If True, return the AUUC value for the training data.
        :param bool valid: If True, return the AUUC value for the validation data.
        :param metric: AUUC metric type. One of:

            - "qini"
            - "lift"
            - "gain"
            - "None" (default; metric set in parameters)

        :returns: Normalized AUUC value for the specified key(s).

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
        ...                                               uplift_metric="kl",
        ...                                               distribution="bernoulli",
        ...                                               min_rows=10,
        ...                                               auuc_type="gain")
        >>> uplift_model.train(y=response_column, x=predictors, training_frame=train)
        >>> uplift_model.auuc_normalized() # <- Default: return training metric value
        >>> uplift_model.auuc_normalized(train=True,  valid=True)
        """
        assert metric in [None, 'qini', 'lift', 'gain'], "AUUC metric "+metric+" should be None, 'qini','lift' or 'gain'."
        return self._delegate_to_metrics(metric=metric, method='auuc_normalized', train=train, valid=valid)

    @deprecated_params_order(old_sig=_old_sig, is_called_with_old_sig=_is_called_with_old_sig)
    def uplift(self, metric="qini", train=False, valid=False):
        """
        Retrieve uplift values for the specified metrics. 
        
        If all are ``False`` (default), then return the training metric uplift values.
        If more than one option is set to ``True``, then return a dictionary of metrics where the 
        keys are "train" and "valid".
        
        :param bool train: If ``True``, return the uplift values for the training data.
        :param bool valid: If ``True``, return the uplift values for the validation data.
        :param metric: Uplift metric type. One of:

            - "qini" (default)
            - "lift"
            - "gain"
            
        :param metric: Uplift metric type ("qini", "lift", "gain", default is "qini")
        :param bool train: If True, return the uplift values for the training data.
        :param bool valid: If True, return the uplift values for the validation data.
        
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
        ...                                               uplift_metric="kl",
        ...                                               distribution="bernoulli",
        ...                                               min_rows=10,
        ...                                               auuc_type="gain")
        >>> uplift_model.train(y=response_column, x=predictors, training_frame=train)
        >>> uplift_model.uplift() # <- Default: return training metric value
        >>> uplift_model.uplift(train=True, metric="gain")
        """
        assert metric in ['qini', 'lift', 'gain'], "Uplift metric "+metric+" should be 'qini','lift' or 'gain'."
        return self._delegate_to_metrics(metric=metric, method='uplift', train=train, valid=valid)

    @deprecated_params_order(old_sig=_old_sig, is_called_with_old_sig=_is_called_with_old_sig)
    def uplift_normalized(self, metric="qini", train=False, valid=False):
        """
        Retrieve normalized uplift values for the specified metrics. 
        
        If all are ``False`` (default), then return the training metric normalized uplift values.
        If more than one option is set to ``True``, then return a dictionary of metrics where the 
        keys are "train" and "valid".
        
        :param bool train: If ``True``, return the uplift values for the training data.
        :param bool valid: If ``True``, return the uplift values for the validation data.
        :param metric: Uplift metric type. One of:

            - "qini" (default)
            - "lift"
            - "gain"
        
        :returns: a list of normalized uplift values for the specified key(s).

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
        ...                                               uplift_metric="kl",
        ...                                               distribution="bernoulli",
        ...                                               min_rows=10,
        ...                                               auuc_type="gain")
        >>> uplift_model.train(y=response_column, x=predictors, training_frame=train)
        >>> uplift_model.uplift_normalized() # <- Default: return training metric value
        >>> uplift_model.uplift_normalized(train=True, metric="gain")
        """
        assert metric in ['qini', 'lift', 'gain'], "Uplift metric "+metric+" should be 'qini','lift' or 'gain'."
        return self._delegate_to_metrics(metric=metric, method='uplift_normalized', train=train, valid=valid)

    def n(self, train=False, valid=False):
        """
        Retrieve numbers of observations.
        
        If all are ``False`` (default), then return the training metric number of observations.
        If more than one option is set to ``True``, then return a dictionary of metrics where the 
        keys are "train" and "valid".
        
        :param bool train: If ``True``, return the number of observations for the training data.
        :param bool valid: If ``True``, return the number of observations for the validation data.
        
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
        ...                                               uplift_metric="kl",
        ...                                               distribution="bernoulli",
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
        
        If all are ``False`` (default), then return the training metric prediction thresholds.
        If more than one option is set to ``True``, then return a dictionary of metrics where the 
        keys are "train" and "valid".
        
        :param bool train: If ``True``, return the prediction thresholds for the training data.
        :param bool valid: If ``True``, return the prediction thresholds for the validation data.
        
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
        ...                                               uplift_metric="kl",
        ...                                               distribution="bernoulli",
        ...                                               min_rows=10,
        ...                                               auuc_type="gain")
        >>> uplift_model.train(y=response_column, x=predictors, training_frame=train)
        >>> uplift_model.thresholds()  # <- Default: return training metric value
        >>> uplift_model.thresholds(train=True)
        """
        return self._delegate_to_metrics(method='thresholds', train=train, valid=valid)

    def thresholds_and_metric_scores(self, train=False, valid=False):
        """
        Retrieve thresholds and metric scores table for the specified metrics. 
        
        If all are ``False`` (default), then return the training metric thresholds and metric scores table.
        If more than one option is set to ``True``, then return a dictionary of metrics where the 
        keys are "train" and "valid".
        
        :param bool train: If ``True``, return the thresholds and metric scores table for the training data.
        :param bool valid: If ``True``, return the thresholds and metric scores table for the validation data.
        
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
        ...                                               uplift_metric="kl",
        ...                                               distribution="bernoulli",
        ...                                               min_rows=10,
        ...                                               auuc_type="gain")
        >>> uplift_model.train(y=response_column, x=predictors, training_frame=train)
        >>> uplift_model.thresholds_and_metric_scores()  # <- Default: return training metric value
        >>> uplift_model.thresholds_and_metric_scores(train=True)
        """
        return self._delegate_to_metrics(method='thresholds_and_metric_scores', train=train, valid=valid)

    def auuc_table(self, train=False, valid=False):
        """
        Retrieve all types of AUUC in a table.
        
        If all are ``False`` (default), then return the training metric AUUC table.
        If more than one option is set to ``True``, then return a dictionary of metrics where the 
        keys are "train" and "valid".
        
        :param bool train: If ``True``, return the AUUC table for the training data.
        :param bool valid: If ``True``, return the AUUC table for the validation data.
         
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
        ...                                               uplift_metric="kl",
        ...                                               distribution="bernoulli",
        ...                                               min_rows=10,
        ...                                               auuc_type="gain")
        >>> uplift_model.train(y=response_column, x=predictors, training_frame=train)
        >>> uplift_model.auuc_table() # <- Default: return training metric value
        >>> uplift_model.auuc_table(train=True)
        """
        return self._delegate_to_metrics(method='auuc_table', train=train, valid=valid)

    def qini(self, train=False, valid=False):
        """
        Retrieve Qini value (area between Qini cumulative uplift curve and random curve)

        If all are False (default), then return the training metric AUUC table.
        If more than one options is set to True, then return a dictionary of metrics where the 
        keys are "train" and "valid".

        :param bool train: If True, return the Qini value for the training data.
        :param bool valid: If True, return the Qini value for the validation data.

        :returns: the Qini value for the specified key(s).

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
        ...                                               uplift_metric="kl",
        ...                                               distribution="bernoulli",
        ...                                               min_rows=10,
        ...                                               auuc_type="gain")
        >>> uplift_model.train(y=response_column, x=predictors, training_frame=train)
        >>> uplift_model.qini() # <- Default: return training metric value
        >>> uplift_model.qini(train=True)
        """
        return self._delegate_to_metrics(method='qini', train=train, valid=valid)

    def _delegate_to_metrics(self, method, train=False, valid=False, **kwargs):
        tm = ModelBase._get_metrics(self, train, valid, xval=None)
        m = {}
        for k, v in viewitems(tm):
            if v is None:
                m[k] = None
            elif hasattr(v, method) and callable(getattr(v, method)):
                m[k] = getattr(v, method)(**kwargs)
            else:
                raise ValueError('no method {} in {}'.format(method, type(v)))
        return list(m.values())[0] if len(m) == 1 else m
