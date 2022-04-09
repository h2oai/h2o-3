from __future__ import division
from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator
import pandas as pd
import numpy as np
import tempfile
from tests import pyunit_utils


def import_gam_mojo_regression(family):
    np.random.seed(1234)
    n_rows = 10

    data = {
        "X1": np.random.randn(n_rows),
        "X2": np.random.randn(n_rows),
        "X3": np.random.randn(n_rows),
        "W": np.random.choice([10, 20], size=n_rows),
        "Y": np.random.choice([0, 0, 0, 0, 0, 10, 20, 30], size=n_rows) + 0.1
    }

    train = h2o.H2OFrame(pd.DataFrame(data))
    test = train.drop("W")
    print(train)
    h2o_model = H2OGeneralizedAdditiveEstimator(family=family,
                                                gam_columns=["X3"],
                                                weights_column="W",
                                                lambda_=0,
                                                bs=[2],
                                                tweedie_variance_power=1.5,
                                                tweedie_link_power=0)
    h2o_model.train(x=["X1", "X2"], y="Y", training_frame=train)
    print(h2o_model)

    predict_w = h2o_model.predict(train)
    # scoring without weight column
    predict = h2o_model.predict(test) 
    
    # get train perf on a cloned frame (to avoid re-using cached metrics - force to recalculate) 
    train_clone = h2o.H2OFrame(train.as_data_frame(use_pandas=True))
    model_perf_on_train = h2o_model.model_performance(test_data=train_clone)

    # ditto on test
    test_clone = h2o.H2OFrame(test.as_data_frame(use_pandas=True))
    model_perf_on_test = h2o_model.model_performance(test_data=test_clone)

    # should produce same frame
    pyunit_utils.compare_frames_local(predict_w, predict, prob=1, tol=1e-6)

    # Save the MOJO to a temporary file
    original_model_filename = tempfile.mkdtemp()
    original_model_filename = h2o_model.save_mojo(original_model_filename)

    # Load the model from the temporary file
    mojo_model = h2o.import_mojo(original_model_filename)

    predict_mojo_w = mojo_model.predict(train)
    predict_mojo = mojo_model.predict(test)

    # Both should produce same results as in-H2O models
    pyunit_utils.compare_frames_local(predict_mojo_w, predict, prob=1, tol=1e-6)
    pyunit_utils.compare_frames_local(predict_mojo, predict, prob=1, tol=1e-6)

    mojo_perf_on_train = mojo_model.model_performance(test_data=train_clone)
    assert abs(mojo_perf_on_train._metric_json["MSE"] - model_perf_on_train._metric_json["MSE"]) < 1e-6

    mojo_perf_on_test = mojo_model.model_performance(test_data=test_clone)
    assert abs(mojo_perf_on_test._metric_json["MSE"] - model_perf_on_test._metric_json["MSE"]) < 1e-6


def import_gam_mojo_poisson():
    import_gam_mojo_regression("poisson")


def import_gam_mojo_tweedie():
    import_gam_mojo_regression("tweedie")


def import_gam_mojo_gamma():
    import_gam_mojo_regression("gamma")


def import_gam_mojo_gaussian():
    import_gam_mojo_regression("gaussian")


pyunit_utils.run_tests([
    import_gam_mojo_poisson,
    import_gam_mojo_tweedie,
    import_gam_mojo_gamma,
    import_gam_mojo_gaussian
])
