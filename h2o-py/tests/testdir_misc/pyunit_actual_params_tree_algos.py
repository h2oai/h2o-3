import sys
sys.path.insert(1,"../../")
import h2o
import os
from tests import pyunit_utils, assert_equals

from h2o.estimators import H2ORandomForestEstimator, H2OGradientBoostingEstimator, H2OIsolationForestEstimator, H2OXGBoostEstimator


def build_model_and_check(model, train, valid, response, ntrees):
    print()
    print("Testing: ", model.algo)
    model.train(y=response, training_frame=train, validation_frame=valid)
    print("Summary")
    print(model.get_summary())
    print("Actual params")
    print(model.actual_params)
    assert_equals(model.get_summary()["number_of_trees"][0], model.params["ntrees"]["actual"], "Actual parameters are not correct. Must be changed according to the model summary.")
    assert_equals(model.get_summary()["number_of_trees"][0], model.actual_params["ntrees"], "Actual parameters are not correct. Must be changed according to the model summary.")
    assert_equals(ntrees, model.params["ntrees"]["input"], "Input parameters are changed. Must be the same.")
    assert ntrees > model.actual_params["ntrees"], "Early stopping or CV is not applied, this test isn't test anything."
    print()


def test_early_stopping_and_cross_validation_correctly_set_actual_params():
    prostate = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    response = "CAPSULE"
    ntrees = 100
    prostate[response] = prostate[response].asfactor()
    train, valid = prostate.split_frame(ratios=[0.8], seed=1234)
    
    rf = H2ORandomForestEstimator(ntrees=ntrees, max_depth=8, nfolds=2, stopping_metric="auc", stopping_rounds=3, stopping_tolerance=0.01, seed=1, score_each_iteration=True)
    build_model_and_check(rf, train, valid, response, ntrees)
    rf = H2ORandomForestEstimator(ntrees=ntrees, max_depth=8, nfolds=0, stopping_metric="auc", stopping_rounds=3, stopping_tolerance=0.01, seed=1, score_each_iteration=True)
    build_model_and_check(rf, train, valid, response, ntrees)

    gbm = H2OGradientBoostingEstimator(ntrees=ntrees, max_depth=8, nfolds=2, stopping_metric="auc", stopping_rounds=3, stopping_tolerance=0.01, seed=1, score_each_iteration=True)
    build_model_and_check(gbm, train, valid, response, ntrees)
    gbm = H2OGradientBoostingEstimator(ntrees=ntrees, max_depth=8, nfolds=0, stopping_metric="auc", stopping_rounds=3, stopping_tolerance=0.01, seed=1, score_each_iteration=True)
    build_model_and_check(gbm, train, valid, response, ntrees)

    isofor = H2OIsolationForestEstimator(ntrees=ntrees, max_depth=8, stopping_metric="AUCPR", stopping_rounds=3, stopping_tolerance=0.01, seed=1, validation_frame=valid, validation_response_column=response, score_each_iteration=True)
    build_model_and_check(isofor, train, valid, response, ntrees)

    xgb = H2OXGBoostEstimator(ntrees=ntrees, max_depth=8, nfolds=2, stopping_metric="auc", stopping_rounds=3, stopping_tolerance=0.01, seed=1, score_each_iteration=True)
    build_model_and_check(xgb, train, valid, response, ntrees)
    xgb = H2OXGBoostEstimator(ntrees=ntrees, max_depth=8, nfolds=0, stopping_metric="auc", stopping_rounds=3, stopping_tolerance=0.01, seed=1, score_each_iteration=True)
    build_model_and_check(xgb, train, valid, response, ntrees)


def test_with_use_best_cv_iteration():
    os.environ["sharedtree.crossvalidation.useBestCVIteration"] = "FALSE"
    test_early_stopping_and_cross_validation_correctly_set_actual_params()
    os.environ["sharedtree.crossvalidation.useBestCVIteration"] = "TRUE"
    test_early_stopping_and_cross_validation_correctly_set_actual_params()


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_with_use_best_cv_iteration)
else:
    test_with_use_best_cv_iteration()
