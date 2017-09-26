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


def stackedensemble_base_model_training_frame_test():
    """This test checks the following:
    1) That passing in base models that use different subsets of 
       the features works. (different x, but same training_frame)
    2) That passing in base models that use different subsets of 
       the features works. (different training_frame) 
    3) TO DO: That passing in base models that use training frames with different nrows fails.
    """

    # Import training set
    train = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/higgs_train_5k.csv"),
                            destination_frame="higgs_train_5k")

    # Identify predictors and response
    x = train.columns
    y = "response"
    x.remove(y)

    # Convert response to a factor
    train[y] = train[y].asfactor()

    # Set number of folds
    nfolds = 3

    # Train and cross-validate a GBM
    my_gbm = H2OGradientBoostingEstimator(distribution="bernoulli",
                                          ntrees=10,
                                          nfolds=nfolds,
                                          fold_assignment="Modulo",
                                          keep_cross_validation_predictions=True,
                                          seed=1)
    my_gbm.train(x=x[1:11], y=y, training_frame=train)

    # Train and cross-validate a RF
    my_rf = H2ORandomForestEstimator(ntrees=10,
                                     nfolds=nfolds,
                                     fold_assignment="Modulo",
                                     keep_cross_validation_predictions=True,
                                     seed=1)
    my_rf.train(x=x[13:20], y=y, training_frame=train)

    # Train a stacked ensemble
    stack1 = H2OStackedEnsembleEstimator(base_models=[my_gbm.model_id, my_rf.model_id])
    stack1.train(x=x, y=y, training_frame=train)

    # Train a stacked ensemble (no x)
    stack2 = H2OStackedEnsembleEstimator(base_models=[my_gbm.model_id, my_rf.model_id])
    stack2.train(y=y, training_frame=train)

    # Eval train AUC to assess equivalence
    assert stack1.auc() == stack2.auc()


    # Next create two different training frames
    train1 = train[list(range(1,11,1))].cbind(train[y])
    train2 = train[list(range(13,20,1))].cbind(train[y])

    # Train and cross-validate a GBM
    my_gbm = H2OGradientBoostingEstimator(distribution="bernoulli",
                                          ntrees=10,
                                          nfolds=nfolds,
                                          fold_assignment="Modulo",
                                          keep_cross_validation_predictions=True,
                                          seed=1)
    my_gbm.train(y=y, training_frame=train1)

    # Train and cross-validate a RF
    my_rf = H2ORandomForestEstimator(ntrees=10,
                                     nfolds=nfolds,
                                     fold_assignment="Modulo",
                                     keep_cross_validation_predictions=True,
                                     seed=1)
    my_rf.train(y=y, training_frame=train2)

    # Train a stacked ensemble
    stack3 = H2OStackedEnsembleEstimator(base_models=[my_gbm.model_id, my_rf.model_id])
    stack3.train(y=y, training_frame=train)


    # This will fail so we check for an exception being raised/failure and pass if it truly fails
    # Create a new training frame that's a different size
    train3 = train2[0:2000,:]

    # Train and cross-validate a RF
    my_rf = H2ORandomForestEstimator(ntrees=10,
                                     nfolds=nfolds,
                                     fold_assignment="Modulo",
                                     keep_cross_validation_predictions=True,
                                     seed=1)
    my_rf.train(y=y, training_frame=train3)

    # Train a stacked ensemble
    stack4 = H2OStackedEnsembleEstimator(base_models=[my_gbm.model_id, my_rf.model_id])
    raised = False
    try:
        stack4.train(y=y, training_frame=train)
    except:
        raised = True

    assert raised is True, "Stacked Ensembles with different training frame sizes should fail"

if __name__ == "__main__":
    pyunit_utils.standalone_test(stackedensemble_base_model_training_frame_test)
else:
    stackedensemble_base_model_training_frame_test()