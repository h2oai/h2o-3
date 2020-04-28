from __future__ import print_function

import os
import sys

sys.path.insert(1, os.path.join("..","..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.targetencoder import TargetEncoder
from h2o.estimators import H2OGradientBoostingEstimator


def test_that_old_te_is_helpful_for_titanic_gbm_xval():

    #Import the titanic dataset
    titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")

    # Set response column as a factor
    titanic['survived'] = titanic['survived'].asfactor()
    response='survived'

    # Split the dataset into train, test
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
    fold_column = "kfold_column"
    train[fold_column] = train.kfold_column(n_folds=5, seed=3456)

    # Train a TE model
    titanic_te = TargetEncoder(x= encoded_columns, y= response,
                                  fold_column= fold_column, blended_avg= blended_avg, inflection_point = inflection_point, smoothing = smoothing)

    titanic_te.fit(frame=train)


    train_te = titanic_te.transform(frame=train, holdout_type="kfold", seed=1234, noise=noise)
    test_te = titanic_te.transform(frame=test, holdout_type="none", noise=0.0)

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

    # To prevent overly optimistic results ( overfitting to xval metrics) metric is computed on yet unseen test split
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


def test_that_old_te_is_helpful_for_titanic_gbm_valid_test_split():

  #Import the titanic dataset
  titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")

  # Set response column as a factor
  titanic['survived'] = titanic['survived'].asfactor()
  response='survived'

  # Split the dataset into train, valid and test
  train, valid, test = titanic.split_frame(ratios = [.7,.15], seed = 1234)

  # Choose which columns to encode
  encoded_columns = ["home.dest", "cabin", "embarked"]

  # Set target encoding parameters
  blended_avg= True
  inflection_point = 3
  smoothing = 10
  # In general, the less data you have the more regularisation you need
  noise = 0.1

  # For k_fold strategy we need to provide fold column
  fold_column = "kfold_column"
  train[fold_column] = train.kfold_column(n_folds=5, seed=3456)

  # Train a TE model
  titanic_te = TargetEncoder(x= encoded_columns, y= response,
                             fold_column= fold_column, blended_avg= blended_avg, inflection_point = inflection_point, smoothing = smoothing)

  titanic_te.fit(frame=train)

  # New target encoded train, test, valid sets
  train_te = titanic_te.transform(frame=train, holdout_type="kfold", seed=1234, noise=noise)
  valid_te = titanic_te.transform(frame=valid, holdout_type="none", noise=0.0)
  test_te = titanic_te.transform(frame=test, holdout_type="none", noise=0.0)

  gbm_with_te=H2OGradientBoostingEstimator(max_depth=6,
                                           min_rows=1,
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
  gbm_with_te.train(x=x_with_te, y=response, training_frame=train_te, validation_frame=valid_te)

  # To prevent overly optimistic results ( overfitting to xval metrics) metric is computed on yet unseen test split
  my_gbm_metrics_train_auc = gbm_with_te.model_performance(train_te).auc()
  print("TE train:" + str(my_gbm_metrics_train_auc))

  my_gbm_metrics_valid_auc = gbm_with_te.model_performance(valid_te).auc()
  print("TE valid:" + str(my_gbm_metrics_valid_auc))

  my_gbm_metrics = gbm_with_te.model_performance(test_te)
  auc_with_te = my_gbm_metrics.auc()

  # auc_with_te = 0.8528
  print("TE test:" + str(auc_with_te))

  gbm_baseline=H2OGradientBoostingEstimator(max_depth=6,
                                            min_rows=1,
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
                    training_frame=train, validation_frame=valid)
  gbm_baseline_metrics = gbm_baseline.model_performance(test)
  auc_baseline = gbm_baseline_metrics.auc()

  # auc_baseline = 0.83953
  print("Baseline test:" + str(auc_baseline))

  assert auc_with_te > auc_baseline


testList = [
    test_that_old_te_is_helpful_for_titanic_gbm_xval,
    test_that_old_te_is_helpful_for_titanic_gbm_valid_test_split
]

pyunit_utils.run_tests(testList)
