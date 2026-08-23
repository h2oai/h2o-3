"""
GH-16681: GLM with control_variables set and cross-validation.

Verifies that control_variables works correctly through the Python client when nfolds > 0:
1. Training succeeds and CV metrics are populated.
2. CV residual deviance with control variables zeroed differs from the no-control-variables baseline.
3. cross_validation_metrics_unrestricted_model is populated and matches an exact oracle: with an
   explicit fold_column, the unrestricted (control-variables-preserved) CV residual deviance must
   equal exactly the same model trained without control_variables, since the fold fits are
   identical (zeroing is scoring-only).
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
        "fold":   [0,1,2,0,1,2,0,1,2,0,1,2,0,1,2,0,1,2,0,1,2,0,1,2,0,1],
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


def test_control_variables_cv_unrestricted_metrics_match_baseline_exactly():
    """
    Exact oracle: with an explicit fold_column (not seed-derived folds), the fold fits for
    glm_ctrl and glm_baseline are identical -- control_variables only changes scoring, not
    training. So cross_validation_metrics_unrestricted_model (control-variables preserved) on
    glm_ctrl must equal glm_baseline's plain cross_validation_metrics exactly, not just "differ
    from the restricted view". This is a stronger check than
    test_control_variables_cv_deviance_differs_from_baseline: a deviance that merely differs from
    baseline would still pass even if the wrong coefficient were zeroed, but an exact match against
    an independently-trained baseline would not.
    """
    train = _make_binomial_offset_frame()

    glm_ctrl = H2OGeneralizedLinearEstimator(
        family="binomial", alpha=[0], lambda_=[0],
        control_variables=["x1"], fold_column="fold", seed=SEED
    )
    glm_ctrl.train(x=["x1", "x2"], y="y", training_frame=train)

    glm_baseline = H2OGeneralizedLinearEstimator(
        family="binomial", alpha=[0], lambda_=[0],
        fold_column="fold", seed=SEED
    )
    glm_baseline.train(x=["x1", "x2"], y="y", training_frame=train)

    unrestricted = glm_ctrl.cross_validation_metrics_unrestricted_model
    assert unrestricted is not None, "cross_validation_metrics_unrestricted_model must be populated"

    dev_unrestricted = unrestricted["residual_deviance"]
    dev_baseline = glm_baseline.model_performance(xval=True).residual_deviance()

    assert abs(dev_unrestricted - dev_baseline) < 1e-8, (
        f"Unrestricted CV residual deviance ({dev_unrestricted:.10f}) must exactly match the "
        f"same-fold-column baseline without control_variables ({dev_baseline:.10f})"
    )


pyunit_utils.run_tests([
    test_control_variables_cv_trains_successfully,
    test_control_variables_cv_deviance_differs_from_baseline,
    test_control_variables_cv_unrestricted_metrics_match_baseline_exactly,
])
