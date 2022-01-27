from __future__ import division
from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator
import pandas as pd
import numpy as np
from tests import pyunit_utils


def gam_train_metrics_recalculate(family):
    np.random.seed(1234)
    n_rows = 1000

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

    # force H2O to recalculate metrics instead just taking them from metrics cache
    train_clone = h2o.H2OFrame(pd.DataFrame(data))

    print("GAM performance with test_data=train: {0}, with test_data=test: {1} and train=True: "
          "{2}".format(h2o_model.model_performance(test_data=train)._metric_json["MSE"],
                       h2o_model.model_performance(test_data=test)._metric_json["MSE"],
                       h2o_model.model_performance(train=True)._metric_json["MSE"]))

    assert abs(h2o_model.model_performance(test_data=train_clone)._metric_json["MSE"] - h2o_model.model_performance(train=True)._metric_json["MSE"]) < 1e-6


def gam_train_metrics_recalculate_poisson():
    gam_train_metrics_recalculate("poisson")


def gam_train_metrics_recalculate_tweedie():
    gam_train_metrics_recalculate("tweedie")


def gam_train_metrics_recalculate_gamma():
    gam_train_metrics_recalculate("gamma")


def gam_train_metrics_recalculate_gaussian():
    gam_train_metrics_recalculate("gaussian")


pyunit_utils.run_tests([
    gam_train_metrics_recalculate_poisson,
    gam_train_metrics_recalculate_tweedie,
    gam_train_metrics_recalculate_gamma,
    gam_train_metrics_recalculate_gaussian
])
