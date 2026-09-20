import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator

# GH-16851: huber derives mean_residual_deviance from the combined holdout PREDICTIONS
# (ModelMetricsRegression.MetricBuilderRegression.computeModelMetrics fills it only when preds != null).
# The aggregated unrestricted cross-validation view has no predictions to supply, so it must be OMITTED
# rather than reported as 0.0 -- which would read as a perfect fit on huber's headline metric.
#
# This lives in Python rather than as a JUnit because GBM huber + cross-validation trips a pre-existing,
# unrelated key leak that fails H2ORunner's per-test leak check (reproduced with remove_offset_effects
# both on and off, so it is not caused by this feature).


def gbm_huber_no_unrestricted_cv():
    fr = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    # Deliberately a large offset: at +-0.3 against AGE (~40-79) the restricted and unrestricted views are
    # near-identical by construction, which would make the "the two views differ" assertions below vacuous.
    fr["offset"] = fr["ID"].cos() * 5

    common = dict(offset_column="offset", remove_offset_effects=True, ntrees=15, max_depth=4,
                  seed=42, nfolds=3, fold_assignment="Modulo")
    x = ["CAPSULE", "RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON"]

    huber = H2OGradientBoostingEstimator(distribution="huber", **common)
    huber.train(x=x, y="AGE", training_frame=fr)

    out = huber._model_json["output"]

    # the primary (offset-removed) CV metric is still produced and is a real number
    assert out["cross_validation_metrics"] is not None, "restricted CV metrics missing"
    restricted_mrd = huber.model_performance(xval=True).mean_residual_deviance()
    assert restricted_mrd > 0, \
        "restricted CV mean_residual_deviance should be > 0, got %r" % restricted_mrd

    # the unrestricted twin is omitted entirely; the bug this pins reported it as 0.0
    assert out["cross_validation_metrics_unrestricted_model"] is None, \
        "unrestricted CV view must be omitted for huber, not reported as 0.0"
    assert huber.unrestricted_model_performance(xval=True) is None, \
        "accessor must return None for huber xval"

    # the train dual view is unaffected -- it scores real predictions, so its mean_residual_deviance
    # is a real number (this is the exact quantity the bug zeroed on the CV side)
    huber_train_unrestricted = huber.unrestricted_model_performance(train=True)
    assert huber_train_unrestricted is not None, \
        "unrestricted TRAINING metrics should still be populated for huber"
    assert huber_train_unrestricted.mean_residual_deviance() > 0, \
        "huber unrestricted TRAINING mean_residual_deviance should be > 0, got %r" % \
        huber_train_unrestricted.mean_residual_deviance()

    # control: a non-huber distribution with the same setup still gets the unrestricted CV view,
    # and its mean_residual_deviance is a real number rather than the 0.0 the bug produced
    gaussian = H2OGradientBoostingEstimator(distribution="gaussian", **common)
    gaussian.train(x=x, y="AGE", training_frame=fr)
    unrestricted_cv = gaussian.unrestricted_model_performance(xval=True)
    assert unrestricted_cv is not None, "gaussian should still get an unrestricted CV view"
    assert unrestricted_cv.mean_residual_deviance() > 0, \
        "gaussian unrestricted CV mean_residual_deviance should be > 0, got %r" % \
        unrestricted_cv.mean_residual_deviance()
    # ...and it is genuinely the OFFSET-APPLIED view, not a copy of the restricted one. Without this a
    # cloneForOffsetAppliedScoring() that silently stopped applying the offset would pass every assertion.
    restricted_cv_mrd = gaussian.model_performance(xval=True).mean_residual_deviance()
    assert abs(unrestricted_cv.mean_residual_deviance() - restricted_cv_mrd) > 1e-6, \
        "unrestricted and restricted CV views must differ when the offset carries signal (%r vs %r)" % \
        (unrestricted_cv.mean_residual_deviance(), restricted_cv_mrd)


if __name__ == "__main__":
    pyunit_utils.standalone_test(gbm_huber_no_unrestricted_cv)
else:
    gbm_huber_no_unrestricted_cv()
