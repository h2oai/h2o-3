#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from __future__ import print_function

import h2o

import sys
sys.path.insert(1,"../../../")  # allow us to run this standalone

from h2o.estimators.random_forest import H2ORandomForestEstimator
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
from tests import pyunit_utils as pu
from tests.pyunit_utils import assert_warn


seed = 1

class StackedEnsembleTest(object):

    def prepare_data(self):
        col_types = ["numeric", "numeric", "numeric", "enum", "enum", "numeric", "numeric", "numeric", "numeric"]
        dat = h2o.upload_file(path=pu.locate("smalldata/extdata/prostate.csv"),
                              destination_frame="prostate_hex",
                              col_types=col_types)
        train, test = dat.split_frame(ratios=[.8], seed=1)
        x = ["CAPSULE", "GLEASON", "RACE", "DPROS", "DCAPS", "PSA", "VOL"]
        y = "AGE"
        return pu.ns(x=x, y=y, train=train, test=test)

    def train_base_models(self, dataset):
        nfolds = 3
        gbm = H2OGradientBoostingEstimator(distribution="gaussian",
                                           max_depth=3,
                                           learn_rate=0.2,
                                           nfolds=nfolds,
                                           fold_assignment="Modulo",
                                           keep_cross_validation_predictions=True,
                                           seed=seed)
        gbm.train(x=dataset.x, y=dataset.y, training_frame=dataset.train)

        rf = H2ORandomForestEstimator(ntrees=10,
                                      nfolds=nfolds,
                                      fold_assignment="Modulo",
                                      keep_cross_validation_predictions=True,
                                      seed=seed)
        rf.train(x=dataset.x, y=dataset.y, training_frame=dataset.train)
        
        xrf = H2ORandomForestEstimator(ntrees=20,
                                       nfolds=nfolds,
                                       histogram_type="Random",
                                       fold_assignment="Modulo",
                                       keep_cross_validation_predictions=True,
                                       seed=seed)
        xrf.train(x=dataset.x, y=dataset.y, training_frame=dataset.train)
        
        return [gbm, rf, xrf]

    def train_stacked_ensemble(self, dataset, base_models, valid=False):
        se = H2OStackedEnsembleEstimator(base_models=[m.model_id for m in base_models], seed=seed)
        se.train(x=dataset.x, y=dataset.y,
                 training_frame=dataset.train, 
                 validation_frame=dataset.test if valid else None)
        return se


class StackedEnsembleBlendingTest(StackedEnsembleTest):

    def prepare_data(self):
        ds = super(self.__class__, self).prepare_data()
        train, blend = ds.train.split_frame(ratios=[.7], seed=seed)
        return ds.extend(train=train, blend=blend)

    def train_base_models(self, dataset):
        gbm = H2OGradientBoostingEstimator(distribution="gaussian",
                                           max_depth=3,
                                           learn_rate=0.2,
                                           seed=seed)
        gbm.train(x=dataset.x, y=dataset.y, training_frame=dataset.train)

        rf = H2ORandomForestEstimator(ntrees=10,
                                      seed=seed)
        rf.train(x=dataset.x, y=dataset.y, training_frame=dataset.train)
        
        xrf = H2ORandomForestEstimator(ntrees=20,
                                       histogram_type="Random",
                                       seed=seed)
        xrf.train(x=dataset.x, y=dataset.y, training_frame=dataset.train)
        return [gbm, rf, xrf]

    def train_stacked_ensemble(self, dataset, base_models, valid=False):
        se = H2OStackedEnsembleEstimator(base_models=[m.model_id for m in base_models], seed=seed)
        se.train(x=dataset.x, y=dataset.y,
                 training_frame=dataset.train,
                 validation_frame=dataset.test if valid else None,
                 blending_frame=dataset.blend)
        return se



def test_suite_stackedensemble_gaussian(blending=False):
    t = StackedEnsembleTest() if not blending else StackedEnsembleBlendingTest()
    
    def test_predict_on_se_model():
        ds = t.prepare_data()
        models = t.train_base_models(ds)
        se = t.train_stacked_ensemble(ds, models)
        
        for i in range(2): # repeat predict to verify consistency
            pred = se.predict(test_data=ds.test)
            assert pred.nrow == ds.test.nrow, "expected " + str(pred.nrow) + " to be equal to " + str(ds.test.nrow)
            assert pred.ncol == 1, "expected " + str(pred.ncol) + " to be equal to 1 but it was equal to " + str(pred.ncol)

        
    def test_se_performance_is_better_than_individual_models():
        ds = t.prepare_data()
        base_models = t.train_base_models(ds)

        def compute_perf(model):
            perf = pu.ns(
                train=model.model_performance(train=True),
                test=model.model_performance(test_data=ds.test)
            )
            print("{} training performance: ".format(model.model_id))
            print(perf.train)
            print("{} test performance: ".format(model.model_id))
            print(perf.test)
            return perf

        base_perfs = {}
        for model in base_models:
            base_perfs[model.model_id] = compute_perf(model)

        
        se = t.train_stacked_ensemble(ds, base_models)
        perf_se = compute_perf(se)


        # Check that stack perf is better (smaller) than the best (smaller) base learner perf:
        # Training RMSE for each base learner
        baselearner_best_rmse_train = min([perf.train.rmse() for perf in base_perfs.values()])
        stack_rmse_train = perf_se.train.rmse()
        print("Best Base-learner Training RMSE:  {}".format(baselearner_best_rmse_train))
        print("Ensemble Training RMSE:  {}".format(stack_rmse_train))
        assert_warn(stack_rmse_train < baselearner_best_rmse_train,
            "expected SE training RMSE would be smaller than the best of base learner training RMSE, but obtained: " \
            "RMSE (SE) = {}, RMSE (best base learner) = {}".format(stack_rmse_train, baselearner_best_rmse_train))

        # Test RMSE for each base learner
        baselearner_best_rmse_test = min([perf.test.rmse() for perf in base_perfs.values()])
        stack_rmse_test = perf_se.test.rmse()
        print("Best Base-learner Test RMSE:  {}".format(baselearner_best_rmse_test))
        print("Ensemble Test RMSE:  {}".format(stack_rmse_test))
        assert_warn(stack_rmse_test < baselearner_best_rmse_test,
            "expected SE test RMSE would be smaller than the best of base learner test RMSE, but obtained: " \
            "RMSE (SE) = {}, RMSE (best base learner) = {}".format(stack_rmse_test, baselearner_best_rmse_test))
        
        
    def test_validation_frame_produces_same_metric_as_perf_test():
        ds = t.prepare_data()
        models = t.train_base_models(ds)
        se = t.train_stacked_ensemble(ds, models, valid=True)
        se_perf = se.model_performance(test_data=ds.test)
        se_perf_validation_frame = se.model_performance(valid=True)
        # since the metrics object is not exactly the same, we can just test that RSME is the same
        assert se_perf.rmse() == se_perf_validation_frame.rmse(), \
            "expected SE test RMSE to be the same as SE validation frame RMSE, but obtained: " \
            "RMSE (perf on test) = {}, RMSE (test passed as validation frame) = {}".format(se_perf.rmse(), se_perf_validation_frame.rmse())

    
    return [pu.tag_test(test, 'blending' if blending else None) for test in [
        test_predict_on_se_model,
        test_se_performance_is_better_than_individual_models,
        test_validation_frame_produces_same_metric_as_perf_test
    ]]


pu.run_tests([
    test_suite_stackedensemble_gaussian(),
    test_suite_stackedensemble_gaussian(blending=True)
])
