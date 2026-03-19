import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
import tempfile


def glm_mojo_control_vars_offset():
    """
    Test GLM MOJO for all combinations of remove_offset_effects and control_variables
    across binomial, gaussian, and tweedie families.
    """
    train = h2o.import_file(pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    train["CAPSULE"] = train["CAPSULE"].asfactor()
    train["RACE"] = train["RACE"].asfactor()
    train["DCAPS"] = train["DCAPS"].asfactor()
    train["DPROS"] = train["DPROS"].asfactor()
    train["offset_col"] = train["AGE"] / 100.0

    combinations = [
        ("baseline", False, None),
        ("remove_offset_effects", True, None),
        ("control_variables", False, ["PSA"]),
        ("both", True, ["PSA"]),
    ]

    # Binomial
    for label, roe, cv in combinations:
        params = dict(family="binomial", offset_column="offset_col", lambda_=0)
        if roe:
            params["remove_offset_effects"] = True
        if cv is not None:
            params["control_variables"] = cv
        compare_mojo("binomial_" + label, "CAPSULE",
                     ["RACE", "DCAPS", "PSA", "VOL", "DPROS", "GLEASON"], train, params)

    # Gaussian
    for label, roe, cv in combinations:
        params = dict(family="gaussian", offset_column="offset_col", lambda_=0)
        if roe:
            params["remove_offset_effects"] = True
        if cv is not None:
            params["control_variables"] = cv
        compare_mojo("gaussian_" + label, "VOL",
                     ["RACE", "DCAPS", "PSA", "DPROS", "GLEASON"], train, params)

    # Verify that features actually change predictions (gaussian as representative family)
    verify_features_change_predictions("gaussian", "VOL",
                                       ["RACE", "DCAPS", "PSA", "DPROS", "GLEASON"], train,
                                       dict(family="gaussian", offset_column="offset_col", lambda_=0))

    # Tweedie (response must be positive)
    train["positive_vol"] = abs(train["VOL"]) + 1
    for label, roe, cv in combinations:
        params = dict(family="tweedie", offset_column="offset_col", lambda_=0,
                      tweedie_variance_power=1.5, tweedie_link_power=0)
        if roe:
            params["remove_offset_effects"] = True
        if cv is not None:
            params["control_variables"] = cv
        compare_mojo("tweedie_" + label, "positive_vol",
                     ["RACE", "DCAPS", "PSA", "DPROS", "GLEASON"], train, params)


def compare_mojo(label, y, x, data, params):
    print("=== {} ===".format(label))
    model = H2OGeneralizedLinearEstimator(**params)
    model.train(x=x, y=y, training_frame=data)

    pred_h2o = model.predict(data)

    mojo_path = model.save_mojo(path=tempfile.mkdtemp())
    mojo_model = h2o.import_mojo(mojo_path)
    pred_mojo = mojo_model.predict(data)

    pyunit_utils.compare_frames_local(pred_h2o, pred_mojo, prob=1, tol=1e-8)
    print("  PASSED: {}".format(label))
    return pred_h2o


def assert_predictions_differ(pred1, pred2, label):
    col = "p0" if "p0" in pred1.columns else "predict"
    max_diff = (pred1[col].asnumeric() - pred2[col].asnumeric()).abs().max()
    assert max_diff > 1e-10, \
        "{}: predictions should differ but max diff = {}".format(label, max_diff)
    print("  DIFFER OK: {} (max_diff={})".format(label, max_diff))


def verify_features_change_predictions(family_label, y, x, data, base_params):
    """Verify that all combinations of RO and CV produce different predictions from each other."""
    pred_base = compare_mojo(family_label + "_base_check", y, x, data, base_params)
    pred_ro   = compare_mojo(family_label + "_ro_check", y, x, data, dict(base_params, remove_offset_effects=True))
    pred_cv   = compare_mojo(family_label + "_cv_check", y, x, data, dict(base_params, control_variables=["PSA"]))
    pred_both = compare_mojo(family_label + "_both_check", y, x, data,
                             dict(base_params, remove_offset_effects=True, control_variables=["PSA"]))

    assert_predictions_differ(pred_base, pred_ro,   family_label + " baseline vs RO")
    assert_predictions_differ(pred_base, pred_cv,   family_label + " baseline vs CV")
    assert_predictions_differ(pred_base, pred_both, family_label + " baseline vs RO+CV")
    assert_predictions_differ(pred_ro,   pred_cv,   family_label + " RO vs CV")
    assert_predictions_differ(pred_ro,   pred_both, family_label + " RO vs RO+CV")
    assert_predictions_differ(pred_cv,   pred_both, family_label + " CV vs RO+CV")


if __name__ == "__main__":
    pyunit_utils.standalone_test(glm_mojo_control_vars_offset)
else:
    glm_mojo_control_vars_offset()
