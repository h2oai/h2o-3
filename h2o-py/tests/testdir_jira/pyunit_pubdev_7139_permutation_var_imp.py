import sys, os
sys.path.insert(1, os.path.join("..", ".."))
import h2o
from h2o.utils.typechecks import is_type
from h2o.estimators import H2OGradientBoostingEstimator, H2OGeneralizedLinearEstimator
from tests import pyunit_utils


def gbm_model_build():
    """
    Train gbm model
    :returns model, training frame 
    """
    prostate_train = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate_train.csv"))
    prostate_train["CAPSULE"] = prostate_train["CAPSULE"].asfactor()

    ntrees = 100
    learning_rate = 0.1
    depth = 5
    min_rows = 10
    # Build H2O GBM classification model:
    gbm_h2o = H2OGradientBoostingEstimator(ntrees=ntrees, learn_rate=learning_rate,
                                           max_depth=depth,
                                           min_rows=min_rows,
                                           distribution="bernoulli")
    gbm_h2o.train(x=list(range(1, prostate_train.ncol)), y="CAPSULE", training_frame=prostate_train)

    # Doing PFI on test data vs train data: In the end, you need to decide whether you want to know how much the 
    # model relies on each feature for making predictions (-> training data) or how much the feature contributes to 
    # the performance of the model on unseen data (-> test data). To the best of my knowledge, there is no research 
    # addressing the question of training vs. test data 
    return gbm_h2o, prostate_train


def test_metrics_gbm():
    """
    test metrics values from the Permutation Variable Importance
    """
    # train model
    model, fr = gbm_model_build()

    # case H2OFrame
    pm_h2o_df = model.permutation_importance(fr, use_pandas=False, metric="AUC")
    for col in ["Relative Importance", "Scaled Importance", "Percentage"]:
        assert isinstance(pm_h2o_df[col][0], float)

    # range in all tests is [1, ncols], first column is str: Importance
    assert is_type(pm_h2o_df[0][0], str)
    # case pandas
    pm_pd_df = model.permutation_importance(fr, use_pandas=True, metric="AUC")
    for col in pm_pd_df.columns:
        assert isinstance(pm_pd_df.iloc[0][col], float)

    metrics = ["AUTO", "MSE", "RMSE", "AUC", "logloss"]
    for metric in metrics:
        pd_pfi = model.permutation_importance(fr, use_pandas=False, metric=metric)
        for col in pd_pfi.col_header[1:]:
            assert isinstance(pd_pfi[col][0], float)


def test_big_data_cars():
    """
    Test big data dataset, with metric logloss. 
    """
    h2o_df = h2o.import_file(path=pyunit_utils.locate("bigdata/laptop/lending-club/loan.csv"))
    predictors = h2o_df.col_names
    response_col = h2o_df.col_names[12]  # loan amount
    predictors.remove(response_col)

    model = H2OGeneralizedLinearEstimator(family="binomial")
    model.train(y=response_col, x=predictors, training_frame=h2o_df)

    metric = "logloss"
    pm_h2o_df = model.permutation_importance(h2o_df, use_pandas=True, metric=metric)

    for pred in predictors:
        if pred == "Variable":
            continue
        assert isinstance(pm_h2o_df.loc[pred, "Relative Importance"], float)  # Relative PFI


pyunit_utils.run_tests([
    test_metrics_gbm,
    test_big_data_cars,
])
