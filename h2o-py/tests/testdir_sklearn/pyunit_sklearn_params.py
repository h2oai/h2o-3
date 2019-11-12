from __future__ import print_function
import os, sys

from sklearn.pipeline import Pipeline

from h2o.sklearn import H2OAutoMLEstimator, H2OGradientBoostingEstimator, H2OScaler, H2OPCA


sys.path.insert(1, os.path.join("..",".."))
from tests import pyunit_utils

seed = 2019


def test_all_params_are_visible_in_get_params():
    pipeline = Pipeline([
        ('standardize', H2OScaler(center=True, scale=False)),
        ('pca', H2OPCA(k=2, seed=seed)),
        ('estimator', H2OGradientBoostingEstimator(ntrees=20, max_depth=5, seed=seed))
    ])
    params = pipeline.get_params()
    assert isinstance(params['standardize'], H2OScaler)
    assert params['standardize__center'] is True
    assert params['standardize__scale'] is False
    assert isinstance(params['pca'], H2OPCA)
    assert params['pca__k'] == 2
    assert params['pca__seed'] == seed
    assert isinstance(params['estimator'], H2OGradientBoostingEstimator)
    assert params['estimator__ntrees'] == 20
    assert params['estimator__max_depth'] == 5
    assert params['estimator__seed'] == seed
    # also the ones that were not set explicitly
    assert params['pca__max_iterations'] is None
    assert params['estimator__learn_rate'] is None


def test_all_params_can_be_set_using_set_params():
    pipeline = Pipeline([
        ('standardize', H2OScaler()),
        ('pca', H2OPCA()),
        ('estimator', H2OGradientBoostingEstimator())
    ])
    pipeline.set_params(
        standardize__center=True,
        standardize__scale=False,
        pca__k=2,
        pca__seed=seed,
        estimator__ntrees=20,
        estimator__max_depth=5,
        estimator__seed=seed
    )
    assert isinstance(pipeline.named_steps.standardize, H2OScaler)
    assert pipeline.named_steps.standardize.center is True
    assert pipeline.named_steps.standardize.scale is False
    assert isinstance(pipeline.named_steps.pca, H2OPCA)
    assert pipeline.named_steps.pca.k == 2
    assert pipeline.named_steps.pca.seed == seed
    assert isinstance(pipeline.named_steps.estimator, H2OGradientBoostingEstimator)
    assert pipeline.named_steps.estimator.ntrees == 20
    assert pipeline.named_steps.estimator.max_depth == 5
    assert pipeline.named_steps.estimator.seed == seed


def test_all_params_are_accessible_as_properties():
    pipeline = Pipeline([
        ('standardize', H2OScaler(center=True, scale=False)),
        ('pca', H2OPCA(k=2, seed=seed)),
        ('estimator', H2OGradientBoostingEstimator(ntrees=20, max_depth=5, seed=seed))
    ])
    assert isinstance(pipeline.named_steps.standardize, H2OScaler)
    assert pipeline.named_steps.standardize.center is True
    assert pipeline.named_steps.standardize.scale is False
    assert isinstance(pipeline.named_steps.pca, H2OPCA)
    assert pipeline.named_steps.pca.k == 2
    assert pipeline.named_steps.pca.seed == seed
    assert isinstance(pipeline.named_steps.estimator, H2OGradientBoostingEstimator)
    assert pipeline.named_steps.estimator.ntrees == 20
    assert pipeline.named_steps.estimator.max_depth == 5
    assert pipeline.named_steps.estimator.seed == seed
    # also the ones that were not set explicitly
    assert pipeline.named_steps.pca.max_iterations is None
    assert pipeline.named_steps.estimator.learn_rate is None


def test_all_params_can_be_set_as_properties():
    pipeline = Pipeline([
        ('standardize', H2OScaler()),
        ('pca', H2OPCA()),
        ('estimator', H2OGradientBoostingEstimator())
    ])
    pipeline.named_steps.standardize.center = True
    pipeline.named_steps.standardize.scale = False
    pipeline.named_steps.pca.k = 2
    pipeline.named_steps.pca.seed = seed
    pipeline.named_steps.estimator.ntrees = 20
    pipeline.named_steps.estimator.max_depth = 5
    pipeline.named_steps.estimator.seed = seed
    params = pipeline.get_params()
    assert isinstance(params['standardize'], H2OScaler)
    assert params['standardize__center'] is True
    assert params['standardize__scale'] is False
    assert isinstance(params['pca'], H2OPCA)
    assert params['pca__k'] == 2
    assert params['pca__seed'] == seed
    assert isinstance(params['estimator'], H2OGradientBoostingEstimator)
    assert params['estimator__ntrees'] == 20
    assert params['estimator__max_depth'] == 5
    assert params['estimator__seed'] == seed


def test_params_conflicting_with_sklearn_api_are_still_available():
    pca = H2OPCA()
    assert pca.transform != 'NONE'
    assert callable(pca.transform), "`transform` method from sklearn API has been replaced by a property"
    # conflicting param can be accessed normally using get_params()
    assert pca.get_params()['transform'] == 'NONE'
    # property is accessible directly using a trailing underscore
    assert pca.transform_ == 'NONE'

    pca = H2OPCA(transform='DEMEAN')
    assert callable(pca.transform), "`transform` method from sklearn API has been replaced by a property"
    assert pca.get_params()['transform'] == 'DEMEAN'
    assert pca.transform_ == 'DEMEAN'

    # conflicting param can be modified normally using set_params()
    pca.set_params(transform='DESCALE')
    assert pca.get_params()['transform'] == 'DESCALE'
    assert pca.transform_ == 'DESCALE'

    # conflicting property can be set directly using a trailing underscore
    pca.transform_ = 'NORMALIZE'
    assert pca.get_params()['transform'] == 'NORMALIZE'
    assert pca.transform_ == 'NORMALIZE'


def test_params_are_correctly_passed_to_underlying_transformer():
    pca = H2OPCA(seed=seed)
    pca.set_params(transform='DEMEAN', k=3)
    pca.model_id = "dummy"
    assert pca._estimator is None
    pca._make_estimator()  # normally done when calling `fit`
    assert pca._estimator
    parms = pca._estimator._parms
    assert parms['seed'] == seed
    assert parms['transform'] == 'DEMEAN'
    assert parms['k'] == 3
    assert parms['model_id'] == "dummy"
    assert parms['max_iterations'] is None


def test_params_are_correctly_passed_to_underlying_estimator():
    estimator = H2OGradientBoostingEstimator(seed=seed)
    estimator.set_params(max_depth=10, learn_rate=0.5)
    estimator.model_id = "dummy"
    assert estimator._estimator is None
    estimator._make_estimator()  # normally done when calling `fit`
    real_estimator = estimator._estimator
    assert real_estimator
    parms = real_estimator._parms
    assert real_estimator.seed == parms['seed'] == seed
    assert real_estimator.max_depth == parms['max_depth'] == 10
    assert real_estimator.learn_rate == parms['learn_rate'] == 0.5
    assert real_estimator._id == parms['model_id'] == "dummy"
    assert real_estimator.training_frame == parms['training_frame'] is None



def test_params_are_correctly_passed_to_underlying_automl():
    estimator = H2OAutoMLEstimator(seed=seed)
    estimator.set_params(max_models=5, nfolds=0)
    estimator.project_name = "dummy"
    assert estimator._estimator is None
    estimator._make_estimator()  # normally done when calling `fit`
    aml = estimator._estimator
    assert aml
    assert aml.build_control["stopping_criteria"]["seed"] == seed
    assert aml.build_control["stopping_criteria"]["max_models"] == 5
    assert aml.build_control["nfolds"] == 0
    assert aml.build_control["project_name"] == "dummy"


pyunit_utils.run_tests([
    test_all_params_are_visible_in_get_params,
    test_all_params_can_be_set_using_set_params,
    test_all_params_are_accessible_as_properties,
    test_all_params_can_be_set_as_properties,
    test_params_conflicting_with_sklearn_api_are_still_available,
    test_params_are_correctly_passed_to_underlying_transformer,
    test_params_are_correctly_passed_to_underlying_estimator,
    test_params_are_correctly_passed_to_underlying_automl,
])
