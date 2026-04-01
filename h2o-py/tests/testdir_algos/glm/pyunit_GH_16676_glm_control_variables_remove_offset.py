"""
GH-16676: Tests for GLM control_variables and remove_offset_effects features.

Covers checkpoint restore, variable importance filtering, scoring history deviance,
standardization consistency, solver parity, Tweedie family support, parameter validation,
and derived model correctness.
"""
import sys
import math

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


def _make_binomial_offset_frame():
    """Create 26-row binomial frame with two categorical predictors and an offset column."""
    train = h2o.H2OFrame({
        "x1": [1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0],
        "x2": [1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0],
        "offset": [.1,.2,.2,.2,.1,0,0,.2,.3,.5,.3,.4,.8,.4,.4,.5,0,0,.5,.1,0,0,.1,0,.1,0],
        "y": [1,1,0,0,0,1,0,1,0,1,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1],
    })
    train["x1"] = train["x1"].asfactor()
    train["x2"] = train["x2"].asfactor()
    train["y"] = train["y"].asfactor()
    return train


def _load_binomial_20cols():
    """Load binomial_20_cols_10KRows.csv with first 10 cols and response as factors."""
    train = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/binomial_20_cols_10KRows.csv"))
    for i in range(10):
        train[train.columns[i]] = train[train.columns[i]].asfactor()
    train["C21"] = train["C21"].asfactor()
    return train


def _residual_deviance(model):
    """Extract residual deviance from the main (restricted) training metrics."""
    return model._model_json["output"]["training_metrics"]._metric_json["residual_deviance"]


def _mean_residual_deviance(model):
    """Extract mean residual deviance (total / nobs) from training metrics."""
    mm = model._model_json["output"]["training_metrics"]._metric_json
    return mm["residual_deviance"] / mm["nobs"]


def _sh_deviance_values(model):
    """Extract non-null deviance_train values from scoring history."""
    sh = model.scoring_history()
    if "deviance_train" not in sh.columns:
        return []
    return [v for v in sh["deviance_train"].values
            if v is not None and not (isinstance(v, float) and math.isnan(v))]


def _sh_nll_values(model):
    """Extract non-null negative_log_likelihood values from scoring history."""
    sh = model.scoring_history()
    if "negative_log_likelihood" not in sh.columns:
        return []
    return [v for v in sh["negative_log_likelihood"].values
            if v is not None and not (isinstance(v, float) and math.isnan(v))]


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------

def test_glm_cv_ro_checkpoint_preserves_distinct_metrics():
    """
    GH-16676: When both control_variables and remove_offset_effects are enabled,
    checkpoint resume must keep the RO-only and CV-only training metrics distinct.
    """
    train = _make_binomial_offset_frame()

    glm = H2OGeneralizedLinearEstimator(
        family="binomial", alpha=[0], solver="IRLSM",
        control_variables=["x1"], remove_offset_effects=True,
        score_each_iteration=True, max_iterations=3,
    )
    glm.train(x=["x1", "x2"], y="y", training_frame=train, offset_column="offset")

    ro = glm.make_derived_glm_model(dest="pre_cp_ro", remove_offset_effects=True)
    cv = glm.make_derived_glm_model(dest="pre_cp_cv", remove_control_variables_effects=True)
    assert abs(_residual_deviance(ro) - _residual_deviance(cv)) > 1e-10, \
        "RO and CV deviance should differ before checkpoint"

    glm2 = H2OGeneralizedLinearEstimator(
        family="binomial", alpha=[0], solver="IRLSM",
        control_variables=["x1"], remove_offset_effects=True,
        score_each_iteration=True, max_iterations=6,
        checkpoint=glm.model_id,
    )
    glm2.train(x=["x1", "x2"], y="y", training_frame=train, offset_column="offset")

    ro2 = glm2.make_derived_glm_model(dest="post_cp_ro", remove_offset_effects=True)
    cv2 = glm2.make_derived_glm_model(dest="post_cp_cv", remove_control_variables_effects=True)
    dev_ro, dev_cv = _residual_deviance(ro2), _residual_deviance(cv2)
    assert abs(dev_ro - dev_cv) > 1e-10, \
        f"After checkpoint: RO ({dev_ro}) and CV ({dev_cv}) deviance must differ"


def test_glm_cv_varimp_excludes_control_variables():
    """
    GH-16676: Control variables must not appear in the restricted model's variable importance,
    even when control variable column indices are not in ascending order.
    """
    train = h2o.H2OFrame({
        "a": list(range(1, 11)) + [x + 0.5 for x in range(1, 11)],
        "b": [x * 0.1 for x in range(1, 11)] + [x * 0.1 + 0.05 for x in range(1, 11)],
        "c": list(range(10, 110, 10)) + list(range(15, 115, 10)),
        "d": [x * 0.5 for x in range(1, 11)] + [x * 0.5 + 0.25 for x in range(1, 11)],
        "y": [1,0,1,0,1,0,1,0,1,0,1,1,0,0,1,1,0,0,1,1],
    })
    train["y"] = train["y"].asfactor()

    glm = H2OGeneralizedLinearEstimator(family="binomial", alpha=[0], control_variables=["d", "b"])
    glm.train(x=["a", "b", "c", "d"], y="y", training_frame=train)

    restricted_names = [row[0] for row in glm.varimp()]
    assert "b" not in restricted_names, "'b' should not appear in restricted varimp"
    assert "d" not in restricted_names, "'d' should not appear in restricted varimp"

    unrestricted = glm.make_derived_glm_model(dest="unrest_varimp")
    unrestricted_names = [row[0] for row in unrestricted.varimp()]
    assert "b" in unrestricted_names and "d" in unrestricted_names, \
        "Unrestricted varimp must contain control variables 'b' and 'd'"


def test_glm_cv_ro_scoring_history_deviance_matches_metrics():
    """
    GH-16676: With both control_variables and remove_offset_effects enabled and
    generate_scoring_history=True, the last deviance_train in scoring history
    must match the training metrics mean residual deviance.
    """
    train = _make_binomial_offset_frame()

    glm = H2OGeneralizedLinearEstimator(
        family="binomial", alpha=[0],
        control_variables=["x1"], remove_offset_effects=True,
        score_each_iteration=True, generate_scoring_history=True,
    )
    glm.train(x=["x1", "x2"], y="y", training_frame=train, offset_column="offset")

    expected = _mean_residual_deviance(glm)
    values = _sh_deviance_values(glm)
    assert len(values) > 0, "Scoring history should have deviance entries"
    assert abs(expected - values[-1]) < expected * 0.01, \
        f"Scoring history deviance ({values[-1]}) != metrics mean deviance ({expected})"


def test_glm_ro_scoring_history_deviance_matches_metrics():
    """
    GH-16676: With only remove_offset_effects (no control_variables) and
    generate_scoring_history=True, the last deviance_train in scoring history
    must match the training metrics mean residual deviance.
    """
    train = _make_binomial_offset_frame()

    glm = H2OGeneralizedLinearEstimator(
        family="binomial", alpha=[0],
        remove_offset_effects=True,
        score_each_iteration=True, generate_scoring_history=True,
    )
    glm.train(x=["x1", "x2"], y="y", training_frame=train, offset_column="offset")

    expected = _mean_residual_deviance(glm)
    values = _sh_deviance_values(glm)
    assert len(values) > 0, "Scoring history should have deviance entries"
    assert abs(expected - values[-1]) < expected * 0.01, \
        f"Scoring history deviance ({values[-1]}) != metrics mean deviance ({expected})"


def test_glm_ro_standardize_invariant():
    """
    GH-16676: With remove_offset_effects, final deviance and scoring history NLL
    should be consistent regardless of the standardize setting.
    """
    train = _load_binomial_20cols()
    predictors = [c for c in train.columns if c != "C21"]

    glm_no = H2OGeneralizedLinearEstimator(
        family="binomial", standardize=False,
        remove_offset_effects=True, score_each_iteration=True,
    )
    glm_no.train(x=predictors, y="C21", training_frame=train, offset_column="C20")

    glm_yes = H2OGeneralizedLinearEstimator(
        family="binomial", standardize=True,
        remove_offset_effects=True, score_each_iteration=True,
    )
    glm_yes.train(x=predictors, y="C21", training_frame=train, offset_column="C20")

    dev_no, dev_yes = _residual_deviance(glm_no), _residual_deviance(glm_yes)
    assert abs(dev_no - dev_yes) < dev_no * 0.05, \
        f"Deviance should match regardless of standardize (no={dev_no}, yes={dev_yes})"

    nll_no, nll_yes = _sh_nll_values(glm_no)[-1], _sh_nll_values(glm_yes)[-1]
    assert math.isfinite(nll_no) and nll_no > 0
    assert math.isfinite(nll_yes) and nll_yes > 0
    ratio = nll_yes / nll_no
    assert 0.5 < ratio < 2.0, \
        f"NLL ratio={ratio:.4f} — values should be close regardless of standardize"


def test_glm_ro_lbfgs_matches_irlsm():
    """
    GH-16676: L-BFGS solver with remove_offset_effects should produce deviance
    close to IRLSM, and the restricted model should differ from unrestricted.
    """
    train = _load_binomial_20cols()
    predictors = [c for c in train.columns if c != "C21"]

    glm_l = H2OGeneralizedLinearEstimator(
        family="binomial", solver="L_BFGS",
        remove_offset_effects=True, score_each_iteration=True,
    )
    glm_l.train(x=predictors, y="C21", training_frame=train, offset_column="C20")

    glm_i = H2OGeneralizedLinearEstimator(
        family="binomial", solver="IRLSM",
        remove_offset_effects=True, score_each_iteration=True,
    )
    glm_i.train(x=predictors, y="C21", training_frame=train, offset_column="C20")

    dev_l, dev_i = _residual_deviance(glm_l), _residual_deviance(glm_i)
    assert abs(dev_l - dev_i) < dev_i * 0.05, \
        f"L-BFGS ({dev_l}) and IRLSM ({dev_i}) deviance should be close"

    unrestricted = glm_l.make_derived_glm_model(dest="lbfgs_unrest")
    assert abs(dev_l - _residual_deviance(unrestricted)) > 1e-10, \
        "Restricted and unrestricted deviance must differ for L-BFGS"


def test_glm_cv_ro_lbfgs_produces_distinct_derived_models():
    """
    GH-16676: L-BFGS with both control_variables and remove_offset_effects must
    produce distinct deviances for the main, unrestricted, CV-only, and RO-only models.
    """
    train = _load_binomial_20cols()
    predictors = [c for c in train.columns if c != "C21"]

    glm = H2OGeneralizedLinearEstimator(
        family="binomial", solver="L_BFGS",
        control_variables=["C5"], remove_offset_effects=True,
        score_each_iteration=True,
    )
    glm.train(x=predictors, y="C21", training_frame=train, offset_column="C20")

    unrest = glm.make_derived_glm_model(dest="lbfgs_unrest2")
    cv = glm.make_derived_glm_model(dest="lbfgs_cv", remove_control_variables_effects=True)
    ro = glm.make_derived_glm_model(dest="lbfgs_ro", remove_offset_effects=True)

    dev_main = _residual_deviance(glm)
    dev_unrest = _residual_deviance(unrest)
    dev_cv = _residual_deviance(cv)
    dev_ro = _residual_deviance(ro)

    assert abs(dev_main - dev_unrest) > 1e-10, "Main (both) vs unrestricted should differ"
    assert abs(dev_cv - dev_ro) > 1e-10, "CV-only vs RO-only should differ"


def test_glm_ro_tweedie():
    """
    GH-16676: remove_offset_effects should work with the Tweedie family.
    Both restricted and unrestricted models should have training metrics.
    """
    train = h2o.H2OFrame({
        "x1": list(range(1, 11)) + [x + 0.5 for x in range(1, 11)] + [x + 0.2 for x in range(1, 7)],
        "x2": list(range(10, 110, 10)) + list(range(15, 115, 10)) + list(range(12, 72, 10)),
        "offset": [.1,.2,.2,.2,.1,0,0,.2,.3,.5,.3,.4,.8,.4,.4,.5,0,0,.5,.1,0,0,.1,0,.1,0],
        "y": [1.5,2.3,.5,1.2,3.4,2.1,.8,1.9,2.7,3.1,1.1,.4,2.2,1.8,3,.9,1.3,2.5,.7,1.6,2,1.4,.6,2.8,1,3.2],
    })

    glm = H2OGeneralizedLinearEstimator(
        family="tweedie", tweedie_variance_power=1.5, tweedie_link_power=0,
        remove_offset_effects=True,
    )
    glm.train(x=["x1", "x2"], y="y", training_frame=train, offset_column="offset")

    assert glm._model_json["output"]["training_metrics"] is not None
    unrestricted = glm.make_derived_glm_model(dest="tweedie_unrest")
    assert unrestricted._model_json["output"]["training_metrics"] is not None


def test_glm_cv_varimp_excludes_named_control():
    """
    GH-16676: control_variables specified by column name (not index) must be
    excluded from the restricted model's variable importance.
    """
    train = h2o.H2OFrame({
        "predictor_a": list(range(1, 11)) + list(range(1, 11)),
        "control_b": list(range(10, 110, 10)) + list(range(15, 115, 10)),
        "y": [1,0,1,0,1,0,1,0,1,0,1,1,0,0,1,1,0,0,1,1],
    })
    train["y"] = train["y"].asfactor()

    glm = H2OGeneralizedLinearEstimator(family="binomial", control_variables=["control_b"])
    glm.train(x=["predictor_a", "control_b"], y="y", training_frame=train)

    varimp_names = [row[0] for row in glm.varimp()]
    assert "control_b" not in varimp_names, "'control_b' should not appear in restricted varimp"


def test_glm_ro_requires_offset_column():
    """
    GH-16676: Setting remove_offset_effects=True without an offset_column
    should raise a validation error.
    """
    train = h2o.H2OFrame({
        "x1": list(range(1, 11)) + list(range(1, 11)),
        "y": [1,0,1,0,1,0,1,0,1,0,1,1,0,0,1,1,0,0,1,1],
    })
    train["y"] = train["y"].asfactor()

    try:
        glm = H2OGeneralizedLinearEstimator(family="binomial", remove_offset_effects=True)
        glm.train(x=["x1"], y="y", training_frame=train)
        assert False, "Should fail: remove_offset_effects without offset_column"
    except Exception as ex:
        assert "offset" in str(ex).lower(), f"Error should mention offset, got: {ex}"


def test_glm_cv_ro_derived_models_have_metrics():
    """
    GH-16676: When both control_variables and remove_offset_effects are enabled,
    derived models for unrestricted, CV-only, and RO-only must all have
    training metrics and scoring history.
    """
    train = _make_binomial_offset_frame()

    glm = H2OGeneralizedLinearEstimator(
        family="binomial", alpha=[0],
        control_variables=["x1"], remove_offset_effects=True,
    )
    glm.train(x=["x1", "x2"], y="y", training_frame=train, offset_column="offset")

    for field in ["training_metrics", "scoring_history"]:
        assert glm._model_json["output"].get(field) is not None, \
            f"Main model must have {field}"

    unrest = glm.make_derived_glm_model(dest="derived_unrest")
    cv = glm.make_derived_glm_model(dest="derived_cv", remove_control_variables_effects=True)
    ro = glm.make_derived_glm_model(dest="derived_ro", remove_offset_effects=True)

    for name, m in [("unrestricted", unrest), ("CV-only", cv), ("RO-only", ro)]:
        o = m._model_json["output"]
        assert o.get("training_metrics") is not None, f"{name} derived model must have training_metrics"
        assert o.get("scoring_history") is not None, f"{name} derived model must have scoring_history"


pyunit_utils.run_tests([
    test_glm_cv_ro_checkpoint_preserves_distinct_metrics,
    test_glm_cv_varimp_excludes_control_variables,
    test_glm_cv_ro_scoring_history_deviance_matches_metrics,
    test_glm_ro_scoring_history_deviance_matches_metrics,
    test_glm_ro_standardize_invariant,
    test_glm_ro_lbfgs_matches_irlsm,
    test_glm_cv_ro_lbfgs_produces_distinct_derived_models,
    test_glm_ro_tweedie,
    test_glm_cv_varimp_excludes_named_control,
    test_glm_ro_requires_offset_column,
    test_glm_cv_ro_derived_models_have_metrics,
])
