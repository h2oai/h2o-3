#!/usr/bin/env python
# -*- encoding: utf-8 -*-
import h2o

import tempfile
import shutil
import sys
sys.path.insert(1,"../../../")  # allow us to run this standalone

from h2o.estimators.random_forest import H2ORandomForestEstimator
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
from tests import pyunit_utils as pu

seed = 1


def prepare_data(blending=False):
    train = h2o.import_file(path=pu.locate("smalldata/testng/higgs_train_5k.csv"))
    test = h2o.import_file(path=pu.locate("smalldata/testng/higgs_test_5k.csv"))
    target = "response"
    for fr in [train, test]:
        fr[target] = fr[target].asfactor()
    ds = pu.ns(x=fr.columns, y=target, train=train, test=test)

    if blending:
        train, blend = train.split_frame(ratios=[.7], seed=seed)
        return ds.extend(train=train, blend=blend)
    else:
        return ds


def train_base_models(dataset, **kwargs):
    model_args = kwargs if hasattr(dataset, 'blend') else dict(nfolds=3, fold_assignment="Modulo", keep_cross_validation_predictions=True, **kwargs)

    gbm = H2OGradientBoostingEstimator(distribution="bernoulli",
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
    return [gbm, rf]


def train_stacked_ensemble(dataset, base_models, **kwargs):
    se = H2OStackedEnsembleEstimator(base_models=base_models, seed=seed)
    se.train(x=dataset.x, y=dataset.y,
             training_frame=dataset.train,
             blending_frame=dataset.blend if hasattr(dataset, 'blend') else None,
             **kwargs)
    return se


def test_suite_stackedensemble_binary_model(blending=False):

    def test_saved_binary_model_produces_same_predictions_as_original():
        ds = prepare_data(blending)
        base_models = train_base_models(ds)
        se_model = train_stacked_ensemble(ds, base_models)
        
        #Predict in ensemble in Py client
        preds_py = se_model.predict(ds.test)
        
        tmp_dir = tempfile.mkdtemp()
        try:
            bin_file = h2o.save_model(se_model, tmp_dir)
            #Load binary model and predict
            bin_model = h2o.load_model(pu.locate(bin_file))
            preds_bin = bin_model.predict(ds.test)
        finally:
            shutil.rmtree(tmp_dir)

        #Predictions from model in Py and binary model should be the same
        pred_diff = preds_bin - preds_py
        assert pred_diff["p0"].max() < 1e-11
        assert pred_diff["p1"].max() < 1e-11
        assert pred_diff["p0"].min() > -1e-11
        assert pred_diff["p1"].min() > -1e-11
    
    return [pu.tag_test(test, 'blending' if blending else None) for test in [
        test_saved_binary_model_produces_same_predictions_as_original
    ]]
    

pu.run_tests([
    test_suite_stackedensemble_binary_model(),
    test_suite_stackedensemble_binary_model(blending=True),
])
