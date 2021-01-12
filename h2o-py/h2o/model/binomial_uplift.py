# -*- encoding: utf-8 -*-
from __future__ import absolute_import, division, print_function, unicode_literals
from h2o.utils.compatibility import *  # NOQA

from h2o.exceptions import H2OValueError
from h2o.utils.typechecks import assert_is_type
from .model_base import ModelBase


class H2OBinomialUpliftModel(ModelBase):

    def gains_uplift(self, train=False, valid=False):
        """
        Get the gain uplift table for the specified metrics.

        If all are False (default), then return the training metric gain uplift table.
        If more than one options is set to True, then return a dictionary of metrics where the 
        keys are "train" and "valid".
        
        :param bool train: If True, return the gains uplift table for the training data.
        :param bool valid: If True, return the gains uplift table for the validation data.

        :returns: The gain uplift table for the specified key(s).

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
        >>> uplift_model.gains_uplift() # <- Default: return training metric value
        >>> uplift_model.gains_uplift(train=True,  valid=True)
        """
        return self._delegate_to_metrics('gains_uplift', train, valid)
    
    def auuc(self, train=False, valid=False):
        """
        Retrieve area under uplift curve (AUUC) value for the specified metrics.
        
        If all are False (default), then return the training metric AUUC value.
        If more than one options is set to True, then return a dictionary of metrics where the 
        keys are "train" and "valid".
        
        :param bool train: If True, return the AUUC value for the training data.
        :param bool valid: If True, return the AUUC value for the validation data.
        
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
        return self._delegate_to_metrics('auuc', train, valid)

    def uplift(self, train=False, valid=False, metric="auto"):
        """
        Retrieve uplift values for the specified metrics. 
        
        If all are False (default), then return the training metric uplift values.
        If more than one options is set to True, then return a dictionary of metrics where the 
        keys are "train" and "valid".
        
        :param bool train: If True, return the uplift values for the training data.
        :param bool valid: If True, return the uplift values for the validation data.
        :param metric AUUC metric type ("qini", "lift", "gain", default is "auto" which means "qini") 
        
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
        >>> uplift_model.uplift(train=True, valid=True, metric="gain")
        """
        return self._delegate_to_metrics(metric, method='uplift', train=train, valid=valid, xval=False)

    def n(self):
        """
        Retrieve numbers of observations for the specified metrics. 
        
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
        >>> uplift_model.n(train=True, valid=False)
        """
        return self._metric_json["thresholds_and_metric_scores"]["n"]

    def thresholds(self):
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
        >>> uplift_model.thresholds(train=True, valid=True)
        """
        return self._metric_json["thresholds_and_metric_scores"]["thresholds"]

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
