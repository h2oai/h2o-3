#!/usr/bin/env python
# -*- encoding: utf-8 -*-
import h2o

import sys
sys.path.insert(1,"../../../")  # allow us to run this standalone

from h2o.estimators.random_forest import H2ORandomForestEstimator
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
from tests import pyunit_utils as pu
from tests.pyunit_utils import assert_warn


seed = 1


def prepare_data(blending=False):
    col_types = ["numeric", "numeric", "numeric", "enum", "enum", "numeric", "numeric", "numeric", "numeric"]
    dat = h2o.upload_file(path=pu.locate("smalldata/extdata/prostate.csv"),
                          destination_frame="prostate_hex",
                          col_types=col_types)
    train, test = dat.split_frame(ratios=[.8], seed=1)
    x = ["CAPSULE", "GLEASON", "RACE", "DPROS", "DCAPS", "PSA", "VOL"]
    y = "AGE"
    ds = pu.ns(x=x, y=y, train=train, test=test)

    if blending:
        train, blend = train.split_frame(ratios=[.7], seed=seed)
        return ds.extend(train=train, blend=blend)
    else:
        return ds


def train_base_models(dataset, **kwargs):
    model_args = kwargs if hasattr(dataset, 'blend') else dict(nfolds=3, fold_assignment="Modulo", keep_cross_validation_predictions=True, **kwargs)

    gbm = H2OGradientBoostingEstimator(distribution="gaussian",
                                       ntrees=10,
                                       max_depth=3,
                                       min_rows=2,
                                       learn_rate=0.2,
                                       seed=seed,
                                       **model_args)
    gbm.train(x=dataset.x, y=dataset.y, training_frame=dataset.train)

    rf = H2ORandomForestEstimator(ntrees=10,
                                  seed=seed,
                                  **model_args)
    rf.train(x=dataset.x, y=dataset.y, training_frame=dataset.train)
    
    xrf = H2ORandomForestEstimator(ntrees=20,
                                   histogram_type="Random",
                                   seed=seed,
                                   **model_args)
    xrf.train(x=dataset.x, y=dataset.y, training_frame=dataset.train)
    
    return [gbm, rf, xrf] 


def train_stacked_ensemble(dataset, base_models, **kwargs):
    se = H2OStackedEnsembleEstimator(base_models=base_models, seed=seed)
    se.train(x=dataset.x, y=dataset.y,
             training_frame=dataset.train,
             blending_frame=dataset.blend if hasattr(dataset, 'blend') else None,
             **kwargs)
    return se


def test_suite_stackedensemble_gaussian(blending=False):
    
    def test_predict_on_se_model():
        ds = prepare_data(blending)
        models = train_base_models(ds)
        se = train_stacked_ensemble(ds, models)
        
        for i in range(2): # repeat predict to verify consistency
            pred = se.predict(test_data=ds.test)
            assert pred.nrow == ds.test.nrow, "expected " + str(pred.nrow) + " to be equal to " + str(ds.test.nrow)
            assert pred.ncol == 1, "expected " + str(pred.ncol) + " to be equal to 1 but it was equal to " + str(pred.ncol)

        
    def test_se_performance_is_better_than_individual_models():
        ds = prepare_data(blending)
        base_models = train_base_models(ds)

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

        
        se = train_stacked_ensemble(ds, base_models)
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
        ds = prepare_data(blending)
        models = train_base_models(ds)
        se = train_stacked_ensemble(ds, models, validation_frame=ds.test)
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
