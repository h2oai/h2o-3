# -*- encoding: utf-8 -*-

import functools
from .publish import *
from .deploy import *
from h2o.estimators.estimator_base import H2OEstimator
from h2o.grid.grid_search import H2OGridSearch
from h2o.automl import H2OAutoML

def _with_function_called_after(original_function, method_called_after):
    @functools.wraps(original_function)
    def run(self, *args, **kwargs):
        result = original_function(self, *args, **kwargs)
        method_called_after(self)
        return result
    return run

H2OEstimator.publish = publish_estimator
H2OEstimator.is_published = is_model_published
H2OEstimator.deploy = deploy
H2OEstimator.is_deployed = is_deployed
H2OEstimator.train = _with_function_called_after(H2OEstimator.train, publish_estimator_automatically)

H2OGridSearch.publish = publish_grid_search
H2OGridSearch.is_published = is_grid_search_published
H2OGridSearch.deploy = deploy_grid_search
H2OGridSearch.is_deployed = is_grid_search_deployed
H2OGridSearch.train = _with_function_called_after(H2OGridSearch.train, publish_grid_search_automatically)

H2OAutoML.publish = publish_automl
H2OAutoML.is_published = is_automl_published
H2OAutoML.deploy = deploy_automl
H2OAutoML.is_deployed = is_automl_deployed
H2OAutoML.train = _with_function_called_after(H2OAutoML.train, publish_automl_automatically)
