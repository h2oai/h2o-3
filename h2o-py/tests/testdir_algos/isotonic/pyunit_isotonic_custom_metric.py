import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.isotonicregression import H2OIsotonicRegressionEstimator


class CustomLoglossFunc:
    def map(self, pred, act, w, o, model):
        import water.util.MathUtils as math
        # for Isotonic Regression, pred[0] corresponds to p1 probability 
        err = 1 - pred[0] if act[0] == 1 else pred[0]
        return [w * math.logloss(err), w]

    def reduce(self, l, r):
        return [l[0] + r[0], l[1] + r[1]]

    def metric(self, l):
        return l[0] / l[1]


def custom_logloss_mm():
    return h2o.upload_custom_metric(CustomLoglossFunc, func_name="logloss", func_file="mm_logloss.py")


def test_custom_metric_with_isotonic_regression():
    df = h2o.import_file(path=pyunit_utils.locate("smalldata/gbm_test/ecology_model.csv"))
    df["Angaus"] = df["Angaus"].asfactor()

    train, calib = df.split_frame(ratios=[.5], destination_frames=["eco_train", "eco_calib"], seed=42)

    model = H2OGradientBoostingEstimator(
        ntrees=100, distribution="bernoulli", min_rows=10, max_depth=5
    )
    model.train(
        x=list(range(2, train.ncol)),
        y="Angaus", training_frame=train, validation_frame=calib
    )

    preds_calib = model.predict(calib)
    isotonic_train = calib[["Angaus"]]
    isotonic_train = isotonic_train.cbind(preds_calib["p1"])

    logloss = custom_logloss_mm()

    h2o_iso_reg = H2OIsotonicRegressionEstimator(out_of_bounds="clip", custom_metric_func=logloss)
    h2o_iso_reg.train(training_frame=isotonic_train, x="p1", y="Angaus")
    print(h2o_iso_reg)
 
    ir_perf = h2o_iso_reg.model_performance(train=True)._metric_json
    model_valid_logloss = model.model_performance(valid=True).logloss()
    assert ir_perf["custom_metric_name"] == "logloss"
    # this has to be true because IR actually sees the true response
    assert ir_perf["custom_metric_value"] < model_valid_logloss


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_custom_metric_with_isotonic_regression)
else:
    test_custom_metric_with_isotonic_regression()
