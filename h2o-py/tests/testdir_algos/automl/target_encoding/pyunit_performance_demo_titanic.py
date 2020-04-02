from __future__ import print_function

import os
import sys

sys.path.insert(1, os.path.join("..","..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators import H2OTargetEncoderEstimator
from h2o.estimators import H2OGradientBoostingEstimator


def test_that_te_is_helpful_for_titanic():

    #Import the titanic dataset
    titanic = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/titanic.csv"), header=1)

    # Set response column as a factor
    titanic['survived'] = titanic['survived'].asfactor()
    response='survived'

    # Split the dataset into train and test
    train, valid, test = titanic.split_frame(ratios = [.8,.1], seed = 1234)

    # Choose which columns to encode
    encoded_columns = ["home.dest", "cabin", "embarked"]

    # Set target encoding parameters
    data_leakage_handling = "k_fold"

    fold_column = "kfold_column"
    train[fold_column] = train.kfold_column(n_folds=5, seed=1234)

    # Train a TE model
    titanic_te = H2OTargetEncoderEstimator(fold_column=fold_column,
                                           data_leakage_handling=data_leakage_handling)
    titanic_te.train(x=encoded_columns,
                                y=response,
                                training_frame=train)

    # New target encoded train and test sets
    train_te = titanic_te.transform(frame=train, data_leakage_handling="k_fold", seed=2345 )
    valid_te = titanic_te.transform(frame=valid, noise=0.0)
    test_te = titanic_te.transform(frame=test, noise=0.0)

    gbm_with_te=H2OGradientBoostingEstimator(score_tree_interval=10,
                                             ntrees=500,
                                             sample_rate=0.8,
                                             col_sample_rate=0.8,
                                             seed=1234,
                                             stopping_rounds=5,
                                             stopping_metric="AUC",
                                             stopping_tolerance=0.001,
                                             model_id="gbm_with_te")

    myX = ["pclass", "sex", "age", "sibsp", "parch", "fare", "cabin_te", "embarked_te", "home.dest_te"]
    print(train_te)
    print(valid_te)

    gbm_with_te.train(x=myX, y=response, training_frame=train_te, validation_frame=valid_te)

    #
    # my_gbm_metrics = gbm_with_te.model_performance(test_te)
    # auc = my_gbm_metrics.auc()
    #
    # print(auc)

    # gbm_baseline=H2OGradientBoostingEstimator(score_tree_interval=10,
    #                                           ntrees=500,
    #                                           sample_rate=0.8,
    #                                           col_sample_rate=0.8,
    #                                           seed=1234,
    #                                           stopping_rounds=5,
    #                                           stopping_metric="AUC",
    #                                           stopping_tolerance=0.001,
    #                                           model_id="gbm_baseline")
    #
    # gbm_baseline.train(x=myX, y=response,
    #                   training_frame=train, validation_frame=valid)
    # gbm_baseline_metrics = gbm_baseline.model_performance(test)
    # auc_baseline = gbm_baseline_metrics.auc()
    #
    # print(auc_baseline)


testList = [
    test_that_te_is_helpful_for_titanic
]

pyunit_utils.run_tests(testList)
