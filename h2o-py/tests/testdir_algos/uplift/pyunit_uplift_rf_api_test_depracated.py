from __future__ import print_function
import sys, os

sys.path.insert(1, os.path.join("..", "..", ".."))
import h2o
from tests import pyunit_utils, assert_equals, assert_not_equal
from h2o.estimators import H2OUpliftRandomForestEstimator


def uplift_random_forest_api_deprecated():
    """Test that you can call uplift methods with parameters in deprecated order and get the same results"""
    seed = 12345
    treatment_column = "treatment"
    response_column = "outcome"
    x_names = ["feature_"+str(x) for x in range(1,13)]

    train_h2o = h2o.upload_file(pyunit_utils.locate("smalldata/uplift/upliftml_train.csv"))
    train_h2o[treatment_column] = train_h2o[treatment_column].asfactor()
    train_h2o[response_column] = train_h2o[response_column].asfactor()

    uplift_model = H2OUpliftRandomForestEstimator(
        ntrees=10,
        max_depth=5,
        treatment_column=treatment_column,
        uplift_metric="kl",
        distribution="bernoulli",
        min_rows=10,
        nbins=1000,
        seed=seed,
        sample_rate=0.99,
        auuc_type="gain"
    )
    uplift_model.train(y=response_column, x=x_names, training_frame=train_h2o)
    perf = uplift_model.model_performance()

    print(perf)
    
    assert_equals(perf.auuc(), uplift_model.auuc(False, False, "gain"))
    assert_equals(None, uplift_model.auuc(False, True, "gain"))
    assert_equals(perf.auuc(), uplift_model.auuc("gain", False))
    assert_equals(perf.auuc(), uplift_model.auuc("gain", True))
    assert_equals(perf.auuc(), uplift_model.auuc("gain", False, None))
    assert_equals(perf.auuc(), uplift_model.auuc(False, False, None))
    assert_equals(None, uplift_model.auuc(False, True, None))
    assert_equals(perf.auuc(), uplift_model.auuc(False, False, metric=None))
    assert_equals(None, uplift_model.auuc(False, True, metric=None))
    assert_equals(perf.auuc(), uplift_model.auuc(True, False, metric=None))

    assert_equals(perf.auuc_normalized(), uplift_model.auuc_normalized(False, False, "gain"))
    assert_equals(None, uplift_model.auuc(False, True, "gain"))
    assert_equals(perf.auuc_normalized(), uplift_model.auuc_normalized("gain", False))
    assert_equals(perf.auuc_normalized(), uplift_model.auuc_normalized("gain", True))
    assert_equals(perf.auuc_normalized(), uplift_model.auuc_normalized("gain", False, None))
    assert_equals(perf.auuc_normalized(), uplift_model.auuc_normalized(False, False, None))
    assert_equals(None, uplift_model.auuc_normalized(False, True, None))
    assert_equals(perf.auuc_normalized(), uplift_model.auuc_normalized(False, False, metric=None))
    assert_equals(None, uplift_model.auuc_normalized(False, True, metric=None))
    assert_equals(perf.auuc_normalized(), uplift_model.auuc_normalized(True, False, metric=None))

    assert_equals(perf.uplift("gain"), uplift_model.uplift(False, False, "gain"))
    assert_equals(None, uplift_model.uplift(False, True, "gain"))
    assert_equals(perf.uplift("gain"), uplift_model.uplift("gain", False))
    assert_equals(perf.uplift("gain"), uplift_model.uplift("gain", True))
    assert_equals(perf.uplift("qini"), uplift_model.uplift(False, False, metric="qini"))
    assert_equals(None, uplift_model.uplift(False, True, metric="gain"))
    assert_equals(perf.uplift("qini"), uplift_model.uplift(True, False, metric="qini"))

    assert_equals(perf.uplift_normalized("gain"), uplift_model.uplift_normalized(False, False, "gain"))
    assert_equals(None, uplift_model.uplift_normalized(False, True, "gain"))
    assert_equals(perf.uplift_normalized("gain"), uplift_model.uplift_normalized("gain", False))
    assert_equals(perf.uplift_normalized("gain"), uplift_model.uplift_normalized("gain", True))
    assert_equals(perf.uplift_normalized("qini"), uplift_model.uplift_normalized(False, False, metric="qini"))
    assert_equals(None, uplift_model.uplift_normalized(False, True, metric="gain"))
    assert_equals(perf.uplift_normalized("qini"), uplift_model.uplift_normalized(True, False, metric="qini"))


if __name__ == "__main__":
    pyunit_utils.standalone_test(uplift_random_forest_api_deprecated)
else:
    uplift_random_forest_api_deprecated()
