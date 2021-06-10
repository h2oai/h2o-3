import os
import sys

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from h2o.automl import H2OAutoML
from h2o.exceptions import H2OValueError
from tests import pyunit_utils as pu


def import_dataset(seed=0):
    df = h2o.import_file(path=pu.locate("smalldata/prostate/prostate.csv"))
    target = "CAPSULE"
    df[target] = df[target].asfactor()
    fr = df.split_frame(ratios=[.8,.1], seed=seed)
    return pu.ns(train=fr[0], valid=fr[1], test=fr[2], target=target, target_idx=1)


def test_params_can_be_set_as_attributes():
    aml = H2OAutoML()
    aml.max_models = 4
    aml.seed = 42
    aml.nfolds = 0

    ds = import_dataset()
    aml.train(y=ds.target, training_frame=ds.train, validation_frame=ds.valid)
    assert aml.leaderboard.nrows == aml.max_models == 4
    assert aml.project_name is not None
    

def test_params_are_validated_in_setter():
    aml = H2OAutoML()
    try:
        aml.nfolds = 1
        assert False, "should have raised"
    except AssertionError as e:
        assert aml.nfolds == 5, "nfolds should have remained to default value"
        assert "nfolds set to 1; use nfolds >=2 if you want cross-validated metrics and Stacked Ensembles or use nfolds = 0 to disable." == str(e)
    aml.nfolds = 3
    assert aml.nfolds == 3
    

def test_non_train_params_are_frozen_after_first_train():
    aml = H2OAutoML(max_models=2, nfolds=0, seed=42)
    ds = import_dataset()
    aml.train(y=ds.target, training_frame=ds.train, validation_frame=ds.valid)
    assert aml.leaderboard.nrows == aml.max_models == 2
    assert aml.leaderboard.columns[1] == 'auc'
    
    try:
        aml.nfolds = 3
        assert False, "should have raised"
    except H2OValueError as e:
        assert "Param ``nfolds`` can not be modified after the first call to ``train``." == str(e)
        assert aml.nfolds == 0
        
    try:
        aml.seed = 24
        assert False, "should have raised"
    except H2OValueError as e:
        assert "Param ``seed`` can not be modified after the first call to ``train``." == str(e)
        assert aml.seed == 42

    assert aml.sort_metric == 'AUTO'
    aml.sort_metric = 'logloss'
    aml.train(y=ds.target, training_frame=ds.train, validation_frame=ds.valid)
    print(aml.leaderboard)
    assert aml.leaderboard.nrows == 4
    assert aml.leaderboard.columns[1] == 'logloss'
    

pu.run_tests([
    test_params_can_be_set_as_attributes,
    test_params_are_validated_in_setter,
    test_non_train_params_are_frozen_after_first_train,
])

