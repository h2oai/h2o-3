import sys, os

sys.path.insert(1, os.path.join("..", ".."))
import h2o
from builtins import range
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from tests import pyunit_utils
from h2o.model.permutation_varimp import permutation_varimp, plot_permutation_var_imp


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
    prostate_test = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate_test.csv"))

    # Log.info("Converting CAPSULE and RACE columns to factors...\n")
    prostate_test["CAPSULE"] = prostate_test["CAPSULE"].asfactor()

    # Doing PFI on test data vs train data: In the end, you need to decide whether you want to know how much the 
    # model relies on each feature for making predictions (-> training data) or how much the feature contributes to 
    # the performance of the model on unseen data (-> test data). To the best of my knowledge, there is no research 
    # addressing the question of training vs. test data 
    return gbm_h2o, prostate_train


def metrics_testing():
    """
    test metrics values from the Permutation Variable Importance
    """
    model, fr = gbm_model_build()
    # case H2OFrame
    pm_h2o_df = permutation_varimp(model, fr, use_pandas=False, metric="auc")
    for col in range(1, pm_h2o_df.ncols):
        assert isinstance(pm_h2o_df[0, col], float)

    # range in all tests is [1, ncols], first column is str: Importance 
    assert isinstance(pm_h2o_df[0, 0], str)

    # case pandas
    pm_pd_df = permutation_varimp(model, fr, use_pandas=True, metric="auc")
    assert isinstance(pm_pd_df.loc[0][0], str)
    for col in pm_pd_df.columns:
        if col == "importance":
            continue
        assert isinstance(pm_pd_df.loc[0][col], float)

    metrics = ["mse", "rmse", "auc", "logloss"]
    for metric in metrics:
        pd_pfi = permutation_varimp(model, fr, use_pandas=False, metric=metric)
        for col in range(1, pd_pfi.ncols):
            assert isinstance(pd_pfi[0, col], float)
    assert isinstance(pd_pfi[0, 0], str)


def perm_var_imp_plot_glm():
    """
       Test that Permutation Feature Importance indicates that only one variable indicates the result
       Dummy test was created, result shows that only "meat (gr)" is an important variable.  
       """
    full = h2o.import_file(path=pyunit_utils.locate("smalldata/jira/full_testset.csv"))

    predictors = ["meat (gr)", "price (eur)", "restaurant", "city", "gender"]
    response_col = "full"

    glm_model = H2OGeneralizedLinearEstimator(family="binomial", lambda_=0, seed=1234)
    glm_model.train(predictors, response_col, training_frame=full)

    metric = "logloss"
    pvi_df = permutation_varimp(glm_model, full, use_pandas=True, metric=metric)

    for col in pvi_df.columns:
        if col == "importance":
            continue
        elif col == "meat (gr)":  # the most influential variable
            assert pvi_df.loc[2][col] > 0.9
        else:
            assert pvi_df.loc[2][col] < 0.1

    plot_permutation_var_imp(pvi_df, glm_model._model_json["algo"], metric=metric)


def perm_var_imp_plot_gbm():
    """
    test Permutation Variable importance plotting, showing it alongside gbm_h2o.varimp_plot() plotting. 
    """
    model, fr = gbm_model_build()
    metric = "auc"
    pm_h2o_df = permutation_varimp(model, fr, use_pandas=True, metric=metric)
    plot_permutation_var_imp(pm_h2o_df, model._model_json["algo"], metric)
    permutation_varimp_oat(model, fr)

    # plot existing varimp 
    model.varimp_plot()


if __name__ == "__main__":
    pyunit_utils.standalone_test(metrics_testing)
    pyunit_utils.standalone_test(perm_var_imp_plot_glm)
    pyunit_utils.standalone_test(perm_var_imp_plot_gbm)
else:
    metrics_testing()
    perm_var_imp_plot_glm()
    perm_var_imp_plot_gbm()
