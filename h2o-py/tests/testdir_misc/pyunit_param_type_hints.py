#!/usr/bin/env python
# -*- encoding: utf-8 -*-
"""
GH-16798: Verify that estimator parameters accept all types documented in type hints.

Each test sets the parameter via the property setter and verifies it doesn't raise.
For types that require an H2O cluster (H2OFrame, str frame keys), we test with
actual frames. For simple types (None, float, int, list, dict), we test directly.
"""
import sys
sys.path.insert(1, "../../")
import h2o
from h2o.estimators import (
    H2OGeneralizedLinearEstimator,
    H2OGeneralizedAdditiveEstimator,
    H2OANOVAGLMEstimator,
    H2OModelSelectionEstimator,
    H2ORuleFitEstimator,
    H2OStackedEnsembleEstimator,
    H2OTargetEncoderEstimator,
    H2OXGBoostEstimator,
)
from h2o.exceptions import H2OTypeError
from tests import pyunit_utils


# ---------------------------------------------------------------------------
# alpha / lambda_ — Optional[Union[float, List[float]]]
# Applies to: GLM, GAM, ANOVAGLM, ModelSelection, RuleFit (lambda_ only)
# ---------------------------------------------------------------------------

def test_glm_alpha_lambda_types():
    """GLM alpha and lambda_ accept None, a single float, or a list of floats."""
    m = H2OGeneralizedLinearEstimator()

    for param in ("alpha", "lambda_"):
        # None
        setattr(m, param, None)
        assert getattr(m, param) is None

        # single float
        setattr(m, param, 0.5)
        assert getattr(m, param) == 0.5

        # single int (numeric)
        setattr(m, param, 1)
        assert getattr(m, param) == 1

        # list of floats
        setattr(m, param, [0.1, 0.5, 0.9])
        assert getattr(m, param) == [0.1, 0.5, 0.9]

    # empty list
    for param in ("alpha", "lambda_"):
        setattr(m, param, [])
        assert getattr(m, param) == []

    # invalid type should raise
    try:
        m.alpha = "not_a_number"
        assert False, "Expected H2OTypeError for alpha='not_a_number'"
    except H2OTypeError:
        pass


def test_glm_alpha_scalar_end_to_end():
    """GLM trains successfully with alpha as a scalar float (not wrapped in a list)."""
    train = h2o.import_file(pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    m = H2OGeneralizedLinearEstimator(alpha=0.5)
    m.train(x=["displacement", "power", "weight"], y="acceleration", training_frame=train)
    assert m.mse() is not None


def test_gam_alpha_lambda_types():
    """GAM alpha and lambda_ accept None, a single float, or a list of floats."""
    m = H2OGeneralizedAdditiveEstimator()

    for param in ("alpha", "lambda_"):
        setattr(m, param, None)
        setattr(m, param, 0.5)
        setattr(m, param, [0.1, 0.5])

    try:
        m.alpha = "bad"
        assert False, "Expected H2OTypeError"
    except H2OTypeError:
        pass


def test_anovaglm_alpha_lambda_types():
    """ANOVAGLM alpha and lambda_ accept None, a single float, or a list of floats."""
    m = H2OANOVAGLMEstimator()

    for param in ("alpha", "lambda_"):
        setattr(m, param, None)
        setattr(m, param, 0.5)
        setattr(m, param, [0.1, 0.5])

    try:
        m.lambda_ = "bad"
        assert False, "Expected H2OTypeError"
    except H2OTypeError:
        pass


def test_modelselection_alpha_lambda_types():
    """ModelSelection alpha and lambda_ accept None, a single float, or a list of floats."""
    m = H2OModelSelectionEstimator()

    for param in ("alpha", "lambda_"):
        setattr(m, param, None)
        setattr(m, param, 0.5)
        setattr(m, param, [0.1, 0.5])

    try:
        m.alpha = {"bad": "type"}
        assert False, "Expected H2OTypeError"
    except H2OTypeError:
        pass


def test_rulefit_lambda_types():
    """RuleFit lambda_ accepts None, a single float, or a list of floats."""
    m = H2ORuleFitEstimator()

    m.lambda_ = None
    m.lambda_ = 0.5
    m.lambda_ = [0.1, 0.5]

    try:
        m.lambda_ = "bad"
        assert False, "Expected H2OTypeError"
    except H2OTypeError:
        pass


# ---------------------------------------------------------------------------
# beta_constraints — Optional[Union[str, dict, H2OFrame]]
# ---------------------------------------------------------------------------

def test_glm_beta_constraints_types():
    """GLM beta_constraints accepts None, str (frame key), dict, or H2OFrame."""
    train = h2o.import_file(pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    predictors = ["displacement", "power", "weight", "year"]
    response = "acceleration"

    # None (on fresh estimator)
    m = H2OGeneralizedLinearEstimator()
    m.beta_constraints = None
    assert m.beta_constraints is None

    # dict
    bc_dict = {
        "displacement": {"lower_bound": -1000, "upper_bound": 1000},
        "power":        {"lower_bound": -1000, "upper_bound": 1000},
        "weight":       {"lower_bound": -1000, "upper_bound": 1000},
        "year":         {"lower_bound": -1000, "upper_bound": 1000},
    }
    m = H2OGeneralizedLinearEstimator()
    m.beta_constraints = bc_dict
    assert m.beta_constraints is not None

    # H2OFrame
    n = len(predictors)
    bc_frame = h2o.H2OFrame({
        "names": predictors,
        "lower_bounds": [-1000] * n,
        "upper_bounds": [1000] * n,
    })
    m = H2OGeneralizedLinearEstimator()
    m.beta_constraints = bc_frame
    assert m.beta_constraints is not None

    # str (frame key) — pass the frame's key as a string
    m = H2OGeneralizedLinearEstimator()
    m.beta_constraints = bc_frame.frame_id
    assert m.beta_constraints is not None

    # None round-trip: set a value, then clear back to None
    m = H2OGeneralizedLinearEstimator()
    m.beta_constraints = bc_frame
    assert m.beta_constraints is not None
    m.beta_constraints = None
    assert m.beta_constraints is None

    # invalid type should raise
    try:
        m = H2OGeneralizedLinearEstimator()
        m.beta_constraints = 12345
        assert False, "Expected H2OTypeError for beta_constraints=12345"
    except H2OTypeError:
        pass

    # train with dict to verify end-to-end
    m = H2OGeneralizedLinearEstimator(beta_constraints=bc_dict)
    m.train(x=predictors, y=response, training_frame=train)
    assert m.mse() is not None

    # train with H2OFrame to verify end-to-end
    m = H2OGeneralizedLinearEstimator(beta_constraints=bc_frame)
    m.train(x=predictors, y=response, training_frame=train)
    assert m.mse() is not None


# ---------------------------------------------------------------------------
# gam_columns — List[Union[str, List[str]]]
# ---------------------------------------------------------------------------

def test_gam_columns_types():
    """GAM gam_columns accepts a list where elements can be str or List[str]."""
    m = H2OGeneralizedAdditiveEstimator()

    # None
    m.gam_columns = None
    assert m.gam_columns is None

    # list of strings (each becomes a single-predictor smoother)
    m.gam_columns = ["C6", "C7", "C8"]
    assert m.gam_columns == [["C6"], ["C7"], ["C8"]]  # normalized to nested

    # list of lists
    m.gam_columns = [["C6"], ["C7", "C8"]]
    assert m.gam_columns == [["C6"], ["C7", "C8"]]

    # mixed
    m.gam_columns = ["C6", ["C7", "C8"]]
    assert m.gam_columns == [["C6"], ["C7", "C8"]]

    # empty list
    m.gam_columns = []
    assert m.gam_columns == []

    # invalid type — a bare string is not a valid list; must pass ["col"] instead
    try:
        m.gam_columns = "single_string"
        assert False, "Expected H2OTypeError"
    except H2OTypeError:
        pass


# ---------------------------------------------------------------------------
# base_models — List[Union[str, H2OEstimator, H2OGridSearch]]
# ---------------------------------------------------------------------------

def test_stacked_ensemble_base_models_types():
    """StackedEnsemble base_models accepts list of str, H2OEstimator, or H2OGridSearch."""
    train = h2o.import_file(pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    train["economy_20mpg"] = (train["economy_20mpg"]).asfactor()
    predictors = ["displacement", "power", "weight"]
    response = "economy_20mpg"

    # list of strings (model IDs)
    m = H2OStackedEnsembleEstimator()
    m.base_models = ["model_1", "model_2"]

    # empty list
    m.base_models = []

    # list of H2OEstimator objects
    from h2o.estimators import H2OGradientBoostingEstimator, H2ORandomForestEstimator
    gbm = H2OGradientBoostingEstimator(nfolds=3, keep_cross_validation_predictions=True, seed=1)
    gbm.train(x=predictors, y=response, training_frame=train)

    drf = H2ORandomForestEstimator(nfolds=3, keep_cross_validation_predictions=True, seed=1)
    drf.train(x=predictors, y=response, training_frame=train)

    se = H2OStackedEnsembleEstimator(base_models=[gbm, drf])
    se.train(x=predictors, y=response, training_frame=train)
    assert se.auc() is not None

    # also works with model IDs as strings
    se2 = H2OStackedEnsembleEstimator(base_models=[gbm.model_id, drf.model_id])
    se2.train(x=predictors, y=response, training_frame=train)
    assert se2.auc() is not None

    # invalid type
    try:
        m = H2OStackedEnsembleEstimator()
        m.base_models = 12345
        assert False, "Expected H2OTypeError for base_models=12345"
    except H2OTypeError:
        pass


# ---------------------------------------------------------------------------
# columns_to_encode — List[Union[str, List[str]]]
# ---------------------------------------------------------------------------

def test_target_encoder_columns_to_encode_types():
    """TargetEncoder columns_to_encode accepts list of str or list of List[str]."""
    m = H2OTargetEncoderEstimator()

    # None
    m.columns_to_encode = None
    assert m.columns_to_encode is None

    # list of strings
    m.columns_to_encode = ["col1", "col2"]
    assert m.columns_to_encode == [["col1"], ["col2"]]

    # list of lists (interaction groups)
    m.columns_to_encode = [["col1", "col2"], ["col3"]]
    assert m.columns_to_encode == [["col1", "col2"], ["col3"]]

    # mixed
    m.columns_to_encode = ["col1", ["col2", "col3"]]
    assert m.columns_to_encode == [["col1"], ["col2", "col3"]]

    # empty list
    m.columns_to_encode = []
    assert m.columns_to_encode == []

    # invalid type — a bare string is not a valid list; must pass ["col"] instead
    try:
        m.columns_to_encode = "single_string"
        assert False, "Expected H2OTypeError"
    except H2OTypeError:
        pass


# ---------------------------------------------------------------------------
# gpu_id — Optional[Union[int, List[int]]]
# ---------------------------------------------------------------------------

def test_xgboost_gpu_id_types():
    """XGBoost gpu_id accepts None, a single int, or a list of ints."""
    m = H2OXGBoostEstimator()

    # None
    m.gpu_id = None
    assert m.gpu_id is None

    # single int
    m.gpu_id = 0
    assert m.gpu_id == 0

    # list of ints
    m.gpu_id = [0, 1]
    assert m.gpu_id == [0, 1]

    # invalid type
    try:
        m.gpu_id = "zero"
        assert False, "Expected H2OTypeError"
    except H2OTypeError:
        pass


pyunit_utils.run_tests([
    test_glm_alpha_lambda_types,
    test_gam_alpha_lambda_types,
    test_anovaglm_alpha_lambda_types,
    test_modelselection_alpha_lambda_types,
    test_rulefit_lambda_types,
    test_gam_columns_types,
    test_target_encoder_columns_to_encode_types,
    test_xgboost_gpu_id_types,
    test_glm_alpha_scalar_end_to_end,
    test_glm_beta_constraints_types,
    test_stacked_ensemble_base_models_types,
])
