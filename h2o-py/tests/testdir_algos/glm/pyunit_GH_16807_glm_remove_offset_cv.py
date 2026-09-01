"""
GH-16807: GLM with remove_offset_effects=True and cross-validation.

Verifies that the remove_offset_effects flag works correctly when nfolds > 0:
1. Training succeeds and CV metrics are populated.
2. CV deviance with offset removed differs from the offset-included baseline.
3. With generate_scoring_history=True, deviance_xval and deviance_se appear in scoring history.
4. With-offset CV metric slots are populated and differ from the offset-removed slots.
5. make_unrestricted_glm_model exposes the with-offset CV metrics as its main CV slot.
"""
import sys
import math

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

SEED = 42

def _make_binomial_offset_frame():
    """26-row binomial frame with categorical predictors and a non-zero offset column."""
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


def test_remove_offset_cv_trains_successfully():
    """GLM with remove_offset_effects=True + nfolds=3 must complete and populate CV metrics."""
    train = _make_binomial_offset_frame()
    glm = H2OGeneralizedLinearEstimator(
        family="binomial", alpha=[0], lambda_=[0],
        remove_offset_effects=True, nfolds=3, seed=SEED
    )
    glm.train(x=["x1", "x2"], y="y", training_frame=train, offset_column="offset")
    assert glm is not None
    assert glm.model_performance(xval=True) is not None, "CV metrics must be populated"


def test_remove_offset_cv_deviance_differs_from_baseline():
    """CV residual deviance with remove_offset_effects=True must differ from the offset-included baseline."""
    train = _make_binomial_offset_frame()

    glm_roe = H2OGeneralizedLinearEstimator(
        family="binomial", alpha=[0], lambda_=[0],
        remove_offset_effects=True, nfolds=3, seed=SEED
    )
    glm_roe.train(x=["x1", "x2"], y="y", training_frame=train, offset_column="offset")

    glm_baseline = H2OGeneralizedLinearEstimator(
        family="binomial", alpha=[0], lambda_=[0],
        remove_offset_effects=False, nfolds=3, seed=SEED
    )
    glm_baseline.train(x=["x1", "x2"], y="y", training_frame=train, offset_column="offset")

    dev_roe = glm_roe.model_performance(xval=True).residual_deviance()
    dev_baseline = glm_baseline.model_performance(xval=True).residual_deviance()

    assert abs(dev_roe - dev_baseline) > 1e-10, (
        f"CV residual deviance must differ between remove_offset_effects=True ({dev_roe:.6f}) "
        f"and False ({dev_baseline:.6f}) when the offset is non-zero"
    )


def test_remove_offset_cv_scoring_history_has_xval_columns():
    """With generate_scoring_history=True and nfolds=3, deviance_xval and deviance_se must appear."""
    train = _make_binomial_offset_frame()
    glm = H2OGeneralizedLinearEstimator(
        family="binomial", alpha=[0], lambda_=[0],
        remove_offset_effects=True, nfolds=3,
        generate_scoring_history=True, score_each_iteration=True, seed=SEED
    )
    glm.train(x=["x1", "x2"], y="y", training_frame=train, offset_column="offset")

    sh = glm.scoring_history()
    assert "deviance_xval" in sh.columns, \
        "deviance_xval must appear in scoring history when remove_offset_effects=True and nfolds=3"
    assert "deviance_se" in sh.columns, \
        "deviance_se must appear in scoring history when remove_offset_effects=True and nfolds=3"

    xval_vals = [v for v in sh["deviance_xval"].values
                 if isinstance(v, float) and not math.isnan(v)]
    assert len(xval_vals) > 0, "deviance_xval must have at least one finite value"
    assert all(v > 0 for v in xval_vals), f"All deviance_xval values must be positive; got: {xval_vals}"


def test_remove_offset_cv_unrestricted_metrics_populated():
    """With remove_offset_effects=True and CV, the with-offset CV metric slots must be present and differ."""
    train = _make_binomial_offset_frame()
    glm = H2OGeneralizedLinearEstimator(
        family="binomial", alpha=[0], lambda_=[0],
        remove_offset_effects=True, nfolds=3, seed=SEED,
    )
    glm.train(x=["x1", "x2"], y="y", training_frame=train, offset_column="offset")

    assert glm.cross_validation_metrics_unrestricted_model is not None, \
        "cross_validation_metrics_unrestricted_model must be populated when remove_offset_effects=True and nfolds>0"
    assert glm.cross_validation_metrics_summary_unrestricted_model is not None, \
        "cross_validation_metrics_summary_unrestricted_model must be populated when remove_offset_effects=True and nfolds>0"

    dev_restricted = glm.model_performance(xval=True).residual_deviance()
    dev_unrestricted = glm.cross_validation_metrics_unrestricted_model["residual_deviance"]
    assert abs(dev_restricted - dev_unrestricted) > 1e-10, (
        f"Restricted ({dev_restricted:.6f}) and unrestricted ({dev_unrestricted:.6f}) "
        f"CV deviance must differ when the offset is non-zero"
    )

    # Regression guard: when remove_offset_effects=False the field must be absent/null.
    glm_no_roe = H2OGeneralizedLinearEstimator(
        family="binomial", alpha=[0], lambda_=[0],
        remove_offset_effects=False, nfolds=3, seed=SEED,
    )
    glm_no_roe.train(x=["x1", "x2"], y="y", training_frame=train, offset_column="offset")
    assert glm_no_roe.cross_validation_metrics_unrestricted_model is None, \
        "cross_validation_metrics_unrestricted_model must be None when remove_offset_effects=False"
    assert glm_no_roe.cross_validation_metrics_summary_unrestricted_model is None, \
        "cross_validation_metrics_summary_unrestricted_model must be None when remove_offset_effects=False"


def test_remove_offset_cv_make_unrestricted_model_propagates_cv():
    """make_unrestricted_glm_model must expose the with-offset CV metrics as the derived model's main CV slot."""
    train = _make_binomial_offset_frame()
    glm = H2OGeneralizedLinearEstimator(
        family="binomial", alpha=[0], lambda_=[0],
        remove_offset_effects=True, nfolds=3, seed=SEED,
        keep_cross_validation_predictions=True,
    )
    glm.train(x=["x1", "x2"], y="y", training_frame=train, offset_column="offset")

    src_unrestricted_dev = glm.cross_validation_metrics_unrestricted_model["residual_deviance"]
    derived = glm.make_unrestricted_glm_model()
    derived_cv_dev = derived.model_performance(xval=True).residual_deviance()

    assert abs(src_unrestricted_dev - derived_cv_dev) < 1e-10, (
        f"Derived model CV deviance ({derived_cv_dev:.6f}) must equal source unrestricted CV "
        f"deviance ({src_unrestricted_dev:.6f})"
    )
    assert abs(glm.model_performance(xval=True).residual_deviance() - derived_cv_dev) > 1e-10, \
        "Derived model CV deviance must differ from source restricted CV deviance"


pyunit_utils.run_tests([
    test_remove_offset_cv_trains_successfully,
    test_remove_offset_cv_deviance_differs_from_baseline,
    test_remove_offset_cv_scoring_history_has_xval_columns,
    test_remove_offset_cv_unrestricted_metrics_populated,
    test_remove_offset_cv_make_unrestricted_model_propagates_cv,
])
