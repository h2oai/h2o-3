# -*- encoding: utf-8 -*-

from .publish import *
from .deploy import *
from h2o.estimators.estimator_base import H2OEstimator

H2OEstimator.publish = publish_estimator
H2OEstimator.deploy = deploy
