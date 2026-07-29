"""
GH-16681: GLM with control_variables set and cross-validation.

Verifies that control_variables works correctly through the Python client when nfolds > 0:
1. Training succeeds and CV metrics are populated.
2. CV residual deviance with control variables zeroed differs from the no-control-variables baseline.

Scope note: additional with-control-variables CV metric views are not yet populated for
control_variables under CV — those are deliverables of later phases and are intentionally
not asserted here.
"""
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

SEED = 42


def _make_binomial_offset_frame():
    """26-row binomial frame with categorical predictors (offset column present but unused as predictor)."""
    train = h2o.H2OFrame({
        "x1":     [1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0],
        "x2":     [1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0],
        "offset": [.1,.2,.2,.2,.1,0,0,.2,.3,.5,.3,.4,.8,.4,.4,.5,0,0,.5,.1,0,0,.1,0,.1,0],
        "y":      [1,1,0,0,0,1,0,1,0,1,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1],
    })
    train["x1"] = train["x1"].asfactor()
    train["x2"] = train["x2"].asfactor()
    train["y"] = train["y"].asfactor()
    return train


def test_control_variables_cv_trains_successfully():
    """GLM with control_variables set + nfolds=3 must complete and populate CV metrics."""
    train = _make_binomial_offset_frame()
    glm = H2OGeneralizedLinearEstimator(
        family="binomial", alpha=[0], lambda_=[0],
        control_variables=["x1"], nfolds=3, seed=SEED
    )
    glm.train(x=["x1", "x2"], y="y", training_frame=train)
    assert glm is not None
    assert glm.model_performance(xval=True) is not None, "CV metrics must be populated"


def test_control_variables_cv_deviance_differs_from_baseline():
    """CV residual deviance with control_variables set must differ from the no-control-variables baseline."""
    train = _make_binomial_offset_frame()

    glm_ctrl = H2OGeneralizedLinearEstimator(
        family="binomial", alpha=[0], lambda_=[0],
        control_variables=["x1"], nfolds=3, seed=SEED
    )
    glm_ctrl.train(x=["x1", "x2"], y="y", training_frame=train)

    glm_baseline = H2OGeneralizedLinearEstimator(
        family="binomial", alpha=[0], lambda_=[0],
        nfolds=3, seed=SEED
    )
    glm_baseline.train(x=["x1", "x2"], y="y", training_frame=train)

    dev_ctrl = glm_ctrl.model_performance(xval=True).residual_deviance()
    dev_baseline = glm_baseline.model_performance(xval=True).residual_deviance()

    assert abs(dev_ctrl - dev_baseline) > 1e-10, (
        f"CV residual deviance must differ between control_variables set ({dev_ctrl:.6f}) "
        f"and unset ({dev_baseline:.6f})"
    )


pyunit_utils.run_tests([
    test_control_variables_cv_trains_successfully,
    test_control_variables_cv_deviance_differs_from_baseline,
])
