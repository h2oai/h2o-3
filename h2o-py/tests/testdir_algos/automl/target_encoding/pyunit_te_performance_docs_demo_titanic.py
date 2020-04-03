from __future__ import print_function

import os
import sys

sys.path.insert(1, os.path.join("..","..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators import H2OTargetEncoderEstimator
from h2o.estimators import H2OGradientBoostingEstimator


def test_that_te_is_helpful_for_titanic_gbm_xval():

    #Import the titanic dataset
    titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")

    # Set response column as a factor
    titanic['survived'] = titanic['survived'].asfactor()
    response='survived'

    # Split the dataset into train and test
    train, test = titanic.split_frame(ratios = [.8], seed = 1234)

    # Choose which columns to encode
    encoded_columns = ["home.dest", "cabin", "embarked"]

    # Set target encoding parameters
    blended_avg= True
    inflection_point = 3
    smoothing = 10
    # In general, the less data you have the more regularisation you need
    noise = 0.15

    # For k_fold strategy we need to provide fold column
    data_leakage_handling = "k_fold"
    fold_column = "kfold_column"
    train[fold_column] = train.kfold_column(n_folds=5, seed=3456)

    # Train a TE model
    titanic_te = H2OTargetEncoderEstimator(fold_column=fold_column,
                                           data_leakage_handling=data_leakage_handling, blending=blended_avg, k=inflection_point, f=smoothing)

    titanic_te.train(x=encoded_columns,
                                y=response,
                                training_frame=train)

    # New target encoded train and test sets
    train_te = titanic_te.transform(frame=train, data_leakage_handling="k_fold", seed=1234, noise=noise)
    test_te = titanic_te.transform(frame=test, noise=0.0)

    gbm_with_te=H2OGradientBoostingEstimator(max_depth=6,
                                             min_rows=1,
                                             fold_column=fold_column,
                                             score_tree_interval=5,
                                             ntrees=10000,
                                             sample_rate=0.8,
                                             col_sample_rate=0.8,
                                             seed=1234,
                                             stopping_rounds=5,
                                             stopping_metric="auto",
                                             stopping_tolerance=0.001,
                                             model_id="gbm_with_te")

    # Training is based on training data with early stopping based on xval performance
    x_with_te = ["pclass", "sex", "age", "sibsp", "parch", "fare", "cabin_te", "embarked_te", "home.dest_te"]
    gbm_with_te.train(x=x_with_te, y=response, training_frame=train_te)

    # To prevent overly optimistic results ( overfitting to validation frame) metric is computed on yet unseen test split
    my_gbm_metrics_train_auc = gbm_with_te.model_performance(train_te).auc()
    print("TE train:" + str(my_gbm_metrics_train_auc))

    my_gbm_metrics = gbm_with_te.model_performance(test_te)
    auc_with_te = my_gbm_metrics.auc()

    # auc_with_te = 0.89493
    print("TE test:" + str(auc_with_te))

    gbm_baseline=H2OGradientBoostingEstimator(max_depth=6,
                                              min_rows=1,
                                              fold_column=fold_column,
                                              score_tree_interval=5,
                                              ntrees=10000,
                                              sample_rate=0.8,
                                              col_sample_rate=0.8,
                                              seed=1234,
                                              stopping_rounds=5,
                                              stopping_metric="auto",
                                              stopping_tolerance=0.001,
                                              model_id="gbm_baseline")

    x_baseline = ["pclass", "sex", "age", "sibsp", "parch", "fare", "cabin", "embarked", "home.dest"]
    gbm_baseline.train(x=x_baseline, y=response,
                      training_frame=train)
    gbm_baseline_metrics = gbm_baseline.model_performance(test)
    auc_baseline = gbm_baseline_metrics.auc()

    # auc_baseline = 0.84174
    print("Baseline test:" + str(auc_baseline))

    assert auc_with_te > auc_baseline


testList = [
    test_that_te_is_helpful_for_titanic_gbm_xval
]

pyunit_utils.run_tests(testList)
