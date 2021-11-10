import os
import sys

sys.path.insert(1, os.path.join("..","..",".."))
from h2o.exceptions import H2OValueError
from h2o.automl import H2OAutoML
from tests import pyunit_utils as pu

pu.load_module("_automl_utils", os.path.join(os.path.dirname(__file__)))
from _automl_utils import import_dataset


def test_params_can_be_set_as_attributes():
    aml = H2OAutoML()
    aml.max_models = 4
    aml.seed = 42
    aml.exclude_algos = ['StackedEnsemble']

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
    aml = H2OAutoML(max_models=2, nfolds=3, seed=42, keep_cross_validation_predictions=True)
    ds = import_dataset()
    aml.train(y=ds.target, training_frame=ds.train, validation_frame=ds.valid)
    assert aml.leaderboard.nrows == aml.max_models + aml.leaderboard["model_id"].grep("StackedEnsemble").sum()
    assert aml.leaderboard.columns[1] == 'auc'
    
    try:
        aml.nfolds = 0
        assert False, "should have raised"
    except H2OValueError as e:
        assert "Param ``nfolds`` can not be modified after the first call to ``train``." == str(e)
        assert aml.nfolds == 3
        
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
    assert aml.leaderboard.nrows == aml.max_models*2 + aml.leaderboard["model_id"].grep("StackedEnsemble").sum()
    assert aml.leaderboard.columns[1] == 'logloss'
    

pu.run_tests([
    test_params_can_be_set_as_attributes,
    test_params_are_validated_in_setter,
    test_non_train_params_are_frozen_after_first_train,
])

