import sys, os

sys.path.insert(1, os.path.join("..", "..", ".."))
import h2o
from tests import pyunit_utils, assert_equals, assert_not_equal
from h2o.estimators import H2OUpliftRandomForestEstimator
from h2o.exceptions import H2OResponseError


def uplift_random_forest_api_smoke():
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

    assert_equals(perf.auuc(), uplift_model.auuc())
    assert_equals(perf.auuc(metric="gain"), uplift_model.auuc())
    assert_not_equal(perf.auuc(metric="qini"), uplift_model.auuc())
    assert_equals(perf.auuc(metric="qini"), uplift_model.auuc(metric="qini"))
    assert_equals(perf.auuc_normalized(), uplift_model.auuc_normalized())
    assert_equals(perf.uplift(), uplift_model.uplift())
    assert_equals(perf.uplift_normalized(), uplift_model.uplift_normalized())
    assert_equals(perf.n(), uplift_model.n())
    assert_equals(perf.thresholds(), uplift_model.thresholds())
    assert_equals(perf.thresholds_and_metric_scores(), uplift_model.thresholds_and_metric_scores())
    assert_equals(perf.auuc_table(), uplift_model.auuc_table())
    assert_equals(perf.qini(), uplift_model.qini())
    assert_equals(perf.ate(), uplift_model.ate())
    assert_equals(perf.att(), uplift_model.att())
    assert_equals(perf.atc(), uplift_model.atc())


if __name__ == "__main__":
    pyunit_utils.standalone_test(uplift_random_forest_api_smoke)
else:
    uplift_random_forest_api_smoke()
