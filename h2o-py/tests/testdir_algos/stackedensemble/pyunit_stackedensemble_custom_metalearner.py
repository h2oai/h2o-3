#!/usr/bin/env python
# -*- encoding: utf-8 -*-
import h2o

import sys
sys.path.insert(1,"../../../")  # allow us to run this standalone

from h2o.estimators.random_forest import H2ORandomForestEstimator
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
from tests import pyunit_utils


def stackedensemble_custom_metalearner_test():
    """This test checks the following:
    1) That H2OStackedEnsembleEstimator `metalearner_nfolds` works correctly
    2) That H2OStackedEnsembleEstimator `metalearner_nfolds` works in concert with `metalearner_nfolds`
    """

    # Import training set
    train = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/higgs_train_5k.csv"),
                            destination_frame="higgs_train_5k")
    test = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/higgs_test_5k.csv"),
                           destination_frame="higgs_test_5k")

    # Identify predictors and response
    x = train.columns
    y = "response"
    x.remove(y)

    # Convert response to a factor
    train[y] = train[y].asfactor()
    test[y] = test[y].asfactor()

    # Set number of folds for base learners
    nfolds = 3

    #Metalearner params for gbm, drf, glm, and deep deeplearning
    gbm_params = {"ntrees" : 100, "max_depth" : 6}
    drf_params = {"ntrees" : 100, "max_depth" : 6}
    glm_params = {"alpha" : 0, "lambda" : 0}
    dl_params  = {"hidden" : [32,32,32], "epochs" : 20} ## small network, runs faster

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


    # Train a stacked ensemble & check that metalearner_algorithm works
    stack_gbm = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf], metalearner_algorithm="gbm",
                                            metalearner_params = gbm_params)
    stack_gbm.train(x=x, y=y, training_frame=train)
    # Check that metalearner_algorithm is a default GBM
    assert(stack_gbm.params['metalearner_algorithm']['actual'] == "gbm")
    # Check that the metalearner is default GBM
    meta_gbm = h2o.get_model(stack_gbm.metalearner()['name'])
    assert meta_gbm.algo == "gbm"
    assert meta_gbm.params['ntrees']['actual'] == 100
    assert meta_gbm.params['max_depth']['actual'] == 6


    # Train a stacked ensemble & metalearner_algorithm "drf"; check that metalearner_algorithm works with CV
    stack_drf = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf], metalearner_algorithm="drf", metalearner_nfolds=3,
                                            metalearner_params = drf_params)
    stack_drf.train(x=x, y=y, training_frame=train)
    # Check that metalearner_algorithm is a default RF
    assert(stack_drf.params['metalearner_algorithm']['actual'] == "drf")
    # Check that CV was performed
    assert(stack_drf.params['metalearner_nfolds']['actual'] == 3)
    meta_drf = h2o.get_model(stack_drf.metalearner()['name'])
    assert meta_drf.algo == "drf"
    assert meta_drf.params['nfolds']['actual'] == 3
    assert meta_drf.params['ntrees']['actual'] == 100
    assert meta_drf.params['max_depth']['actual'] == 6


    # Train a stacked ensemble & check that metalearner_algorithm "glm" works
    stack_glm = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf], metalearner_algorithm="glm",
                                            metalearner_params = glm_params)
    stack_glm.train(x=x, y=y, training_frame=train)
    # Check that metalearner_algorithm is a default GLM
    assert(stack_glm.params['metalearner_algorithm']['actual'] == "glm")
    # Check that the metalearner is default GLM
    meta_glm = h2o.get_model(stack_glm.metalearner()['name'])
    assert meta_glm.algo == "glm"
    assert meta_glm.params['alpha']['actual'] == [0.0]
    assert meta_glm.params['lambda']['actual'] == [0.0]


    # Train a stacked ensemble & check that metalearner_algorithm "deeplearning" works
    stack_dl = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf], metalearner_algorithm="deeplearning",
                                           metalearner_params = dl_params)
    stack_dl.train(x=x, y=y, training_frame=train)
    # Check that metalearner_algorithm is a default DNN
    assert(stack_dl.params['metalearner_algorithm']['actual'] == "deeplearning")
    # Check that the metalearner is default DNN
    meta_dl = h2o.get_model(stack_dl.metalearner()['name'])
    assert(meta_dl.algo == "deeplearning")
    assert meta_dl.params['hidden']['actual'] == [32, 32, 32]
    assert meta_dl.params['epochs']['actual'] == 20.0



if __name__ == "__main__":
    pyunit_utils.standalone_test(stackedensemble_custom_metalearner_test)
else:
    stackedensemble_custom_metalearner_test()
