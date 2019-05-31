from h2o.estimators.xgboost import *
from h2o.estimators.gbm import *
from tests import pyunit_utils
import numpy as np


def train_models(iter):
    print("Iteration %s" % iter)
    
    # generating test data using approach from 
    #   https://github.com/dmlc/xgboost/blob/master/tests/python/test_monotone_constraints.py
    number_of_dpoints = 1000
    x1_positively_correlated_with_y = np.random.random(size=number_of_dpoints)
    x2_negatively_correlated_with_y = np.random.random(size=number_of_dpoints)
    
    noise = np.random.normal(loc=0.0, scale=0.01, size=number_of_dpoints)
    y = (
        5 * x1_positively_correlated_with_y + np.sin(10 * np.pi * x1_positively_correlated_with_y) -
        5 * x2_negatively_correlated_with_y - np.cos(10 * np.pi * x2_negatively_correlated_with_y) +
        noise
    )
    
    data = np.column_stack((x1_positively_correlated_with_y, x2_negatively_correlated_with_y, y))
    train = H2OFrame(data, column_names=["x1", "x2", "y"])
    
    monotone_constraints = {
        "x1": 1,
        "x2": -1
    }
    
    gbm_params = {
        "seed": 42,
        "monotone_constraints": monotone_constraints
    }
    
    gbm_model = H2OGradientBoostingEstimator(**gbm_params)
    gbm_model.train(y="y", training_frame=train)
    
    xgboost_params = {
        "tree_method": "exact",
        "seed": 123,
        "max_depth": 5,
        "learn_rate": 0.1,
        "backend": "cpu", # CPU Backend is forced for the results to be comparable
        "monotone_constraints": monotone_constraints
    }
    
    xgboost_model = H2OXGBoostEstimator(**xgboost_params)
    xgboost_model.train(y="y", training_frame=train)

    x1_vals = list(train["x1"].quantile().as_data_frame(use_pandas=True)["x1Quantiles"].values)
    prev = None
    for x1_val in x1_vals:
        test = H2OFrame(x2_negatively_correlated_with_y, column_names=["x2"])
        test["x1"] = x1_val
        curr = gbm_model.predict(test)
        if prev is not None:
            diff = curr - prev
            assert diff.min() >= 0
        prev = curr

    x2_vals = list(train["x2"].quantile().as_data_frame(use_pandas=True)["x2Quantiles"].values)
    prev = None
    for x2_val in x2_vals:
        test = H2OFrame(x1_positively_correlated_with_y, column_names=["x1"])
        test["x2"] = x2_val
        curr = gbm_model.predict(test)
        if prev is not None:
            diff = curr - prev
            assert diff.max() <= 0
        prev = curr

    return gbm_model.rmse(), xgboost_model.rmse()


def gbm_monotone_synthetic_test():
    metrics = np.array(list(map(train_models, range(10))))
    mean_rmse = metrics.mean(axis=0)
    print("GBM RMSE: %s, XGBoost RMSE: %s" % (mean_rmse[0], mean_rmse[1]))
    assert (mean_rmse[0]-mean_rmse[1])/mean_rmse[1] <= 0.07


if __name__ == "__main__":
    pyunit_utils.standalone_test(gbm_monotone_synthetic_test)
else:
    gbm_monotone_synthetic_test()
