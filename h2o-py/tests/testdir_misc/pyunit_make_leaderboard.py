import os
import sys

sys.path.insert(1, os.path.join("..", "..", ".."))
import h2o
import h2o.exceptions
from tests import pyunit_utils, dataset_prostate, CustomMaeFunc
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


def test_make_leaderboard_without_leaderboard_frame():
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


def test_make_leaderboard_with_leaderboard_frame():
    train = h2o.upload_file(pyunit_utils.locate("smalldata/titanic/titanic_expanded.csv"))
    train["name"] = train["name"].asfactor()
    y = "fare"

    aml = H2OAutoML(seed=1234, max_models=5)
    aml.train(y=y, training_frame=train)

    aml2 = H2OAutoML(seed=134, max_models=5)
    aml2.train(y=y, training_frame=train)

    grid = H2OGridSearch(H2OGradientBoostingEstimator(), hyper_params={"ntrees": [1, 2, 3]})
    grid.train(y=y, training_frame=train)
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


def test_make_leaderboard_custom_metric():
    custom_mae = h2o.upload_custom_metric(CustomMaeFunc, func_name="mae", func_file="mm_mae.py")
    custom_mae2 = h2o.upload_custom_metric(CustomMaeFunc, func_name="mae2", func_file="mm_mae.py")

    ftrain, fvalid, _ = dataset_prostate()
    nfolds = 5
    
    model_mae = H2OGradientBoostingEstimator(model_id="prostate", ntrees=1000, max_depth=5,
                                                  score_each_iteration=True,
                                                  stopping_metric="mae",
                                                  stopping_tolerance=0.1,
                                                  stopping_rounds=3, nfolds=nfolds,
                                                  seed=123)
    model_mae.train(y="AGE", x=ftrain.names, training_frame=ftrain, validation_frame=fvalid)

    model_custom1 = H2OGradientBoostingEstimator(model_id="prostate_custom1", ntrees=1000, max_depth=5,
                                                score_each_iteration=True,
                                                custom_metric_func=custom_mae,
                                                stopping_metric="custom",
                                                stopping_tolerance=0.1,
                                                stopping_rounds=3, nfolds=nfolds,
                                                keep_cross_validation_predictions=True,
                                                seed=123)
    model_custom1.train(y="AGE", x=ftrain.names, training_frame=ftrain, validation_frame=fvalid)

    model_custom2 = H2OGradientBoostingEstimator(model_id="prostate_custom2", ntrees=1000, max_depth=2,
                                                 score_each_iteration=True,
                                                 custom_metric_func=custom_mae,
                                                 stopping_metric="custom",
                                                 stopping_tolerance=0.1,
                                                 stopping_rounds=3, nfolds=nfolds,
                                                 keep_cross_validation_predictions=True,
                                                 seed=123)
    model_custom2.train(y="AGE", x=ftrain.names, training_frame=ftrain, validation_frame=fvalid)

    model_custom3 = H2OGradientBoostingEstimator(model_id="prostate_custom3", ntrees=2, max_depth=2,
                                             score_each_iteration=True,
                                             custom_metric_func=custom_mae,
                                             stopping_metric="custom",
                                             stopping_tolerance=0.1,
                                             stopping_rounds=3, nfolds=nfolds,
                                             seed=123)
    model_custom3.train(y="AGE", x=ftrain.names, training_frame=ftrain, validation_frame=fvalid)

    model_custom_alt = H2OGradientBoostingEstimator(model_id="prostate_custom_alt", ntrees=2, max_depth=2,
                                             score_each_iteration=True,
                                             custom_metric_func=custom_mae2,
                                             stopping_metric="custom",
                                             stopping_tolerance=0.1,
                                             stopping_rounds=3, nfolds=nfolds,
                                             seed=123)
    model_custom_alt.train(y="AGE", x=ftrain.names, training_frame=ftrain, validation_frame=fvalid)

    model_se = H2OStackedEnsembleEstimator(base_models=[model_custom1, model_custom2], metalearner_algorithm="gbm", custom_metric_func=custom_mae)
    model_se.train(y="AGE", x=ftrain.names, training_frame=ftrain, validation_frame=fvalid)

    assert "custom" in h2o.make_leaderboard([model_custom1, model_custom2, model_custom3, model_se], fvalid).columns
    ldb = h2o.make_leaderboard([model_custom1, model_custom2, model_custom3, model_se], fvalid).as_data_frame()
    print(ldb)
    assert (ldb["mae"] == ldb["custom"]).all()
    
    ldb_custom = h2o.make_leaderboard([model_custom1, model_custom2, model_custom3, model_se], fvalid, sort_metric="custom").as_data_frame()
    ldb_mae = h2o.make_leaderboard([model_custom1, model_custom2, model_custom3, model_se], fvalid, sort_metric="mae").as_data_frame()

    assert (ldb_mae["model_id"] == ldb_custom["model_id"]).all()

    for scoring_data in ["train", "valid", "xval", "AUTO"]:
        print(scoring_data)
        ldb_custom = h2o.make_leaderboard([model_custom1, model_custom2, model_custom3, model_se], sort_metric="custom", scoring_data=scoring_data).as_data_frame()
        ldb_mae = h2o.make_leaderboard([model_custom1, model_custom2, model_custom3, model_se], sort_metric="mae", scoring_data=scoring_data).as_data_frame()

        print(ldb_custom)
        print(ldb_mae)
        assert (ldb_mae["model_id"] == ldb_custom["model_id"]).all()
        assert (ldb_mae["mae"] == ldb_mae["custom"]).all()
        assert (ldb_custom["mae"] == ldb_custom["custom"]).all()

    try:
        print(h2o.make_leaderboard([model_custom1, model_mae], fvalid))
        assert False, "Should fail - different metrics present."
    except Exception:
        pass
    
    try:
        print(h2o.make_leaderboard([model_custom1, model_custom_alt], fvalid))
        assert False, "Should fail - different custom metrics present."
    except Exception:
        pass


pyunit_utils.run_tests([
    test_leaderboard_with_automl_uses_eventlog,
    test_make_leaderboard_without_leaderboard_frame,
    test_make_leaderboard_with_leaderboard_frame,
    test_make_leaderboard_unsupervised,
    test_make_leaderboard_uplift,
    test_make_leaderboard_custom_metric,
])
