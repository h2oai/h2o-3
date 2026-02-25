import sys, tempfile
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

FAMILIES = [
    dict(family="gaussian", data="regression"),
    dict(family="binomial", data="binomial"),
    dict(family="tweedie",  data="regression", tweedie_variance_power=1.5, tweedie_link_power=-0.5),
]


def make_data(dtype):
    if dtype == "regression":
        train = h2o.create_frame(rows=5000, cols=10, factors=10, has_response=True,
                                 response_factors=1, positive_response=True, missing_fraction=0, seed=1234)
    elif dtype == "binomial":
        train = h2o.create_frame(rows=5000, cols=10, factors=10, has_response=True,
                                 response_factors=2, missing_fraction=0, seed=1234)
    return train.split_frame(ratios=[0.8], seed=1234)


def check_mojo(model, test, pred_h2o):
    mojo_path = model.download_mojo(path=tempfile.mkdtemp())
    mojo_model = h2o.upload_mojo(mojo_path)
    pred_mojo = mojo_model.predict(test.drop("response"))
    common_cols = [c for c in pred_h2o.columns if c in pred_mojo.columns]
    prob_cols = [c for c in common_cols if c in ("p0", "p1")]
    cols = prob_cols if prob_cols else common_cols
    pyunit_utils.compare_frames_local(pred_h2o[cols], pred_mojo[cols], prob=1, tol=1e-6)


def run_family_test(spec, standardize):
    print("\n--- %s, standardize=%s ---" % (spec["family"], standardize))
    train, test = make_data(spec["data"])
    extra = {k: v for k, v in spec.items() if k != "data"}

    model = H2OGeneralizedLinearEstimator(
        lambda_=0, alpha=0.001,
        standardize=standardize, control_variables=["C1", "C2"],
        **extra
    )
    model.train(x=[c for c in train.columns if c != "response"], y="response", training_frame=train)

    pred_restricted = model.predict(test)
    unrestricted = model.make_unrestricted_glm_model()
    pred_unrestricted = unrestricted.predict(test)

    col = "p0" if "p0" in pred_restricted.columns else "predict"
    max_diff = (pred_restricted[col].asnumeric() - pred_unrestricted[col].asnumeric()).abs().max()
    assert max_diff > 1e-10, \
        "%s (standardize=%s): restricted vs unrestricted max diff = %e" % (spec["family"], standardize, max_diff)

    check_mojo(model, test, pred_restricted)
    check_mojo(unrestricted, test, pred_unrestricted)


def glm_mojo_control_variables():
    for spec in FAMILIES:
        for standardize in [False, True]:
            run_family_test(spec, standardize)


if __name__ == "__main__":
    pyunit_utils.standalone_test(glm_mojo_control_variables)
else:
    glm_mojo_control_variables()
