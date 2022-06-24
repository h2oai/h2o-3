from __future__ import print_function

import os
import sys

sys.path.insert(1, os.path.join("..", "..", ".."))
import h2o
import h2o.exceptions
from tests import pyunit_utils
from h2o.automl import H2OAutoML
from h2o.estimators import *
from h2o.grid import H2OGridSearch


def test_leaderboard_with_automl_uses_eventlog():
    train = h2o.upload_file(pyunit_utils.locate("smalldata/titanic/titanic_expanded.csv"))
    train["name"] = train["name"].asfactor()
    y = "fare"

    aml1 = H2OAutoML(seed=1234, max_models=5, project_name="a1")
    aml1.train(y=y, training_frame=train)

    aml2 = H2OAutoML(seed=234, max_models=5, project_name="a1")
    aml2.train(y=y, training_frame=train)

    assert aml2.event_log["message"].grep("New models will be added").nrow > 0
    assert aml1.event_log["message"].grep("Adding model ").nrow > 0
    assert aml2.event_log["message"].grep("Adding model ").nrow > 0


def test_make_leaderboard():
    train = h2o.upload_file(pyunit_utils.locate("smalldata/titanic/titanic_expanded.csv"))
    train["name"] = train["name"].asfactor()
    y = "fare"

    aml = H2OAutoML(seed=1234, max_models=5)
    aml.train(y=y, training_frame=train)

    aml2 = H2OAutoML(seed=134, max_models=5)
    aml2.train(y=y, training_frame=train)

    grid = H2OGridSearch(H2OGradientBoostingEstimator(), hyper_params={"ntrees": [1, 2, 3]})
    grid.train(y=y, training_frame=train)

    assert h2o.make_leaderboard(aml).nrow > 0
    assert h2o.make_leaderboard(aml).nrow == h2o.make_leaderboard(aml).nrow  # creating the same leaderboard doesn't end up with duplicate models
    assert h2o.make_leaderboard(grid).nrow > 0
    assert h2o.make_leaderboard([aml, aml2, grid, aml.leader]).nrow > 0

    # without leaderboard frame
    for score_data in ("AUTO", "xval", "valid", "train"):
        assert h2o.make_leaderboard(aml, scoring_data=score_data).nrow > 0
        assert h2o.make_leaderboard([aml, aml2], scoring_data=score_data).nrow > 0
        assert h2o.make_leaderboard(grid, scoring_data=score_data).nrow > 0
        assert h2o.make_leaderboard([aml, grid, aml2.leader], scoring_data=score_data).nrow > 0

    try:
        print(h2o.make_leaderboard(aml, extra_columns="predict_time_per_row_ms"))

        assert False, "Should fail - Cannot calculate the predict time without leaderboard frame"
    except h2o.exceptions.H2OResponseError:
        pass

    # with leaderboard frame
    expected_cols = ("model_id", "rmse", "mse", "mae", "rmsle", "mean_residual_deviance",
                     "training_time_ms", "predict_time_per_row_ms", "algo")
    ldb = h2o.make_leaderboard(aml, train, extra_columns="ALL")

    for c in expected_cols:
        assert c in ldb.columns

    for score_data in ("AUTO", "xval", "valid", "train"):
        assert h2o.make_leaderboard(aml, train, scoring_data=score_data).nrow > 0
        assert h2o.make_leaderboard([aml, aml2], train, scoring_data=score_data).nrow > 0
        assert h2o.make_leaderboard(grid, scoring_data=score_data).nrow > 0
        assert h2o.make_leaderboard([aml, grid, aml2.leader], train, scoring_data=score_data).nrow > 0

    # extra columns
    for ec in ("training_time_ms", "predict_time_per_row_ms", "algo"):
        assert ec in h2o.make_leaderboard(grid, train, extra_columns=ec).columns

    # extra columns without leaderboard frame
    for ec in ("training_time_ms", "algo"):
        assert ec in h2o.make_leaderboard(grid, extra_columns=ec).columns

    # sort metrics
    for sm in ("rmse", "mse", "mae", "rmsle", "mean_residual_deviance"):
        assert h2o.make_leaderboard(grid, train, sort_metric=sm).columns[1] == sm


def test_make_leaderboard_unsupervised():
    train = h2o.upload_file(pyunit_utils.locate("smalldata/titanic/titanic_expanded.csv"))
    train["name"] = train["name"].asfactor()

    pca = H2OPrincipalComponentAnalysisEstimator()
    pca.train(training_frame=train)

    kmeans = H2OKMeansEstimator(k=5)
    kmeans.train(training_frame=train)

    try:
        print(h2o.make_leaderboard([pca, kmeans]))
        assert False, "Should have failed - no support for unsupervised models"
    except h2o.exceptions.H2OServerError:
        pass


def test_make_leaderboard_uplift():
    data = h2o.upload_file(pyunit_utils.locate("smalldata/uplift/criteo_uplift_13k.csv"))
    predictors = ["f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8"]
    response = "conversion"
    data[response] = data[response].asfactor()
    treatment_column = "treatment"
    data[treatment_column] = data[treatment_column].asfactor()
    train, valid = data.split_frame(ratios=[.8], seed=1234)
    uplift1 = H2OUpliftRandomForestEstimator(ntrees=10,
                                             max_depth=5,
                                             treatment_column=treatment_column,
                                             uplift_metric="KL",
                                             min_rows=10,
                                             seed=1234,
                                             auuc_type="qini")
    uplift1.train(x=predictors,
                  y=response,
                  training_frame=train,
                  validation_frame=valid)

    uplift2 = H2OUpliftRandomForestEstimator(ntrees=10,
                                             max_depth=5,
                                             treatment_column=treatment_column,
                                             uplift_metric="KL",
                                             min_rows=10,
                                             seed=123,
                                             auuc_type="qini")
    uplift2.train(x=predictors,
                  y=response,
                  training_frame=train,
                  validation_frame=valid)
    try:
        print(h2o.make_leaderboard([uplift1, uplift2]))
        assert False, "Should have failed - no support for unsupervised models"
    except h2o.exceptions.H2OServerError:
        pass


pyunit_utils.run_tests([
    test_leaderboard_with_automl_uses_eventlog,
    test_make_leaderboard,
    test_make_leaderboard_unsupervised,
    test_make_leaderboard_uplift,
])
