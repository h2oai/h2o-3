#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from __future__ import print_function

import h2o

import sys
sys.path.insert(1,"../../../")  # allow us to run this standalone

from h2o.estimators.random_forest import H2ORandomForestEstimator
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
from tests import pyunit_utils


def stackedensemble_nfolds_test():
    """This test checks the following:
    1) That H2OStackedEnsembleEstimator `metalearner_nfolds` works correctly
    2) That H2OStackedEnsembleEstimator `metalearner_fold_assignment` works correctly
    3) That Stacked Ensemble cross-validation metrics are correctly copied from metalearner
    """

    # Import training set
    train = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/higgs_train_5k.csv"),
                            destination_frame="higgs_train_5k")
    test = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/higgs_test_5k.csv"),
                            destination_frame="higgs_test_5k")
    # Add a fold_column
    fold_column = "fold_id"
    train[fold_column] = train.kfold_column(n_folds=3, seed=1)

    # Identify predictors and response
    x = train.columns
    y = "response"
    x.remove(y)
    x.remove(fold_column)

    # Convert response to a factor
    train[y] = train[y].asfactor()
    test[y] = test[y].asfactor()

    # Set number of folds for base learners
    nfolds = 3

    # Train and cross-validate a GBM
    my_gbm = H2OGradientBoostingEstimator(distribution="bernoulli",
                                          ntrees=10,
                                          nfolds=nfolds,
                                          fold_assignment="Modulo",
                                          keep_cross_validation_predictions=True,
                                          seed=1)
    my_gbm.train(x=x, y=y, training_frame=train)

    # Train and cross-validate a RF
    my_rf = H2ORandomForestEstimator(ntrees=50,
                                     nfolds=nfolds,
                                     fold_assignment="Modulo",
                                     keep_cross_validation_predictions=True,
                                     seed=1)
    my_rf.train(x=x, y=y, training_frame=train)

    
    # Check that not setting nfolds still produces correct results
    stack0 = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf])
    stack0.train(x=x, y=y, training_frame=train)
    assert(stack0.params['metalearner_nfolds']['actual'] == 0)
    meta0 = h2o.get_model(stack0.metalearner()['name'])
    assert(meta0.params['nfolds']['actual'] == 0)


    # Train a stacked ensemble & check that metalearner_nfolds works
    # Also test that the xval metrics from metalearner & ensemble are equal
    stack1 = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf], metalearner_nfolds=3)
    stack1.train(x=x, y=y, training_frame=train)
    # Check that metalearner_nfolds is correctly stored in model output
    assert(stack1.params['metalearner_nfolds']['actual'] == 3)
    # Check that the metalearner was cross-validated with the correct number of folds
    meta1 = h2o.get_model(stack1.metalearner()['name'])
    assert(meta1.params['nfolds']['actual'] == 3)
    # Check that metalearner fold_assignment is NULL/"AUTO"
    assert(meta1.params['fold_assignment']['actual'] == "AUTO")
    # Check that validation metrics are NULL
    assert(stack1.mse(valid=True) is None)
    # Check that xval metrics from metalearner and ensemble are equal (use mse as proxy)
    assert(stack1.mse(xval=True) == meta1.mse(xval=True))


    # Train a new ensmeble, also passing a validation frame
    ss = test.split_frame(ratios=[0.5], seed=1)
    stack2 = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf], metalearner_nfolds=3)
    stack2.train(x=x, y=y, training_frame=train, validation_frame=ss[0])
    # Check that valid & xval metrics from metalearner and ensemble are equal (use mse as proxy)
    meta2 = h2o.get_model(stack2.metalearner()['name'])
    assert(stack2.mse(valid=True) == meta2.mse(valid=True))
    # Check that xval metrics from metalearner and ensemble are equal (use mse as proxy)
    assert(stack2.mse(xval=True) == meta2.mse(xval=True))


    # Check that metalearner_fold_assignment works
    stack3 = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf], metalearner_nfolds=3, metalearner_fold_assignment="Modulo")
    stack3.train(x=x, y=y, training_frame=train)
    # Check that metalearner_fold_assignment is correctly stored in model output
    assert(stack3.params['metalearner_fold_assignment']['actual'] == "Modulo")
    # Check that the metalearner was cross-validated with the correct number of folds
    meta3 = h2o.get_model(stack3.metalearner()['name'])
    assert(meta3.params['fold_assignment']['actual'] == "Modulo")


    # Check that metalearner_fold_column works
    stack4 = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf], metalearner_fold_column=fold_column,
                                         metalearner_params=dict(keep_cross_validation_models=True))
    stack4.train(x=x, y=y, training_frame=train)
    # Check that metalearner_fold_column is correctly stored in model output
    assert(stack4.params['metalearner_fold_column']['actual']['column_name'] == fold_column)
    # Check that metalearner_fold_column is passed through to metalearner
    meta4 = h2o.get_model(stack4.metalearner()['name'])
    assert(meta4.params['fold_column']['actual']['column_name'] == fold_column)
    assert(meta4.params['nfolds']['actual'] == 0)
    assert(len(meta4.cross_validation_models()) == 3)


if __name__ == "__main__":
    pyunit_utils.standalone_test(stackedensemble_nfolds_test)
else:
    stackedensemble_nfolds_test()