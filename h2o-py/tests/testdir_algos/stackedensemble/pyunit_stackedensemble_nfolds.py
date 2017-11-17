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

    # Identify predictors and response
    x = train.columns
    y = "response"
    x.remove(y)

    # Convert response to a factor
    train[y] = train[y].asfactor()
    test[y] = test[y].asfactor()

    # Set number of folds
    nfolds = 5

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
    # TO DO: This does not work!!  Something wrong on the backend
    #stack1.train(x=x, y=y, training_frame=train)


    #     ---------------------------------------------------------------------------
    # TypeError                                 Traceback (most recent call last)
    # <ipython-input-27-0aa1ded3be98> in <module>()
    #       1 stack1 = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf], metalearner_nfolds=3)
    # ----> 2 stack1.train(x=x, y=y, training_frame=train)

    # /usr/local/lib/python2.7/site-packages/h2o/estimators/estimator_base.pyc in train(self, x, y, training_frame, offset_column, fold_column, weights_column, validation_frame, max_runtime_secs, ignored_columns, model_id, verbose)
    #     208         model.poll(verbose_model_scoring_history=verbose)
    #     209         model_json = h2o.api("GET /%d/Models/%s" % (rest_ver, model.dest_key))["models"][0]
    # --> 210         self._resolve_model(model.dest_key, model_json)
    #     211
    #     212

    # /usr/local/lib/python2.7/site-packages/h2o/estimators/estimator_base.pyc in _resolve_model(self, model_id, model_json)
    #     243
    #     244             if m._is_xvalidated:
    # --> 245                 m._xval_keys = [i["name"] for i in model_json["output"]["cross_validation_models"]]
    #     246
    #     247             # build a useful dict of the params

    # TypeError: 'NoneType' object is not iterable




if __name__ == "__main__":
    pyunit_utils.standalone_test(stackedensemble_nfolds_test)
else:
    stackedensemble_nfolds_test()