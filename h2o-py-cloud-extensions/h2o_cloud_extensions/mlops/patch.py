# -*- encoding: utf-8 -*-

import functools
from .publish import *
from .deploy import *
from h2o.estimators.estimator_base import H2OEstimator

def with_function_called_after(original_function, method_called_after):
    @functools.wraps(original_function)
    def run(self, *args, **kwargs):
        result = original_function(self, *args, **kwargs)
        method_called_after(self)
        return result
    return run

H2OEstimator.publish = publish_estimator
H2OEstimator.deploy = deploy
H2OEstimator.train = with_function_called_after(H2OEstimator.train, publish_estimator_automatically)


    
