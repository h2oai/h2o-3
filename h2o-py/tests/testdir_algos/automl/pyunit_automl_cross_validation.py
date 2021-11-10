from __future__ import print_function
import sys, os
from itertools import cycle, islice
from random import uniform

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from h2o.automl import H2OAutoML
from tests import pyunit_utils as pu

from _automl_utils import import_dataset, get_partitioned_model_names

max_models = 2


def test_nfolds_param():
    print("Check nfolds is passed through to base models")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml_nfolds3", nfolds=3, max_models=3, seed=1)
    aml.train(y=ds.target, training_frame=ds.train)
    base_models = get_partitioned_model_names(aml.leaderboard).base
    amodel = h2o.get_model(base_models[0])
    assert amodel.params['nfolds']['actual'] == 3


def test_nfolds_eq_0():
    print("Check nfolds = 0 works properly")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml_nfolds0", nfolds=0, max_models=3, seed=1)
    aml.train(y=ds.target, training_frame=ds.train)
    base_models = get_partitioned_model_names(aml.leaderboard).base
    amodel = h2o.get_model(base_models[0])
    assert amodel.params['nfolds']['actual'] == 0


def test_fold_column():
    print("Check fold_column param")
    ds = import_dataset()
    fold_column = "fold_id"
    nrows = ds.train.nrows
    train = ds.train.concat(h2o.H2OFrame(list(islice(cycle(range(3)), 0, nrows)), column_names=[fold_column]))
    aml = H2OAutoML(project_name="py_aml_fold_column", max_models=3, seed=1, keep_cross_validation_models=True)
    aml.train(y=ds.target, training_frame=train, fold_column=fold_column)
    models = get_partitioned_model_names(aml.leaderboard)
    amodel = h2o.get_model(models.base[0])
    assert amodel.params['fold_column']['actual']['column_name'] == fold_column
    ensemble = h2o.get_model(models.se[0])
    metalearner = h2o.get_model(ensemble.metalearner()['name'])
    assert metalearner.params['fold_column']['actual']['column_name'] == fold_column
    assert len(metalearner.cross_validation_models()) == 3


def test_weights_column():
    print("Check weights_column")
    ds = import_dataset()
    nrows = ds.train.nrows
    weights_column = "weight"
    train = ds.train.concat(h2o.H2OFrame(list(map(lambda _: uniform(0, 5), range(nrows))), column_names=[weights_column]))
    aml = H2OAutoML(project_name="py_aml_weights_column", max_models=3, seed=1)
    aml.train(y=ds.target, training_frame=train, weights_column=weights_column)
    base_models = get_partitioned_model_names(aml.leaderboard).base
    amodel = h2o.get_model(base_models[0])
    assert amodel.params['weights_column']['actual']['column_name'] == weights_column


def test_fold_column_with_weights_column():
    print("Check fold_column and weights_column")
    ds = import_dataset()
    fold_column = "fold_id"
    weights_column = "weight"
    nrows = ds.train.nrows
    train = (ds.train
             .concat(h2o.H2OFrame(list(islice(cycle(range(3)), 0, nrows)), column_names=[fold_column]))
             .concat(h2o.H2OFrame(list(map(lambda _: uniform(0, 5), range(nrows))), column_names=[weights_column])))
    aml = H2OAutoML(project_name="py_aml_weights_column", max_models=3, seed=1)
    aml.train(y=ds.target, training_frame=train, fold_column=fold_column, weights_column=weights_column)
    base_models = get_partitioned_model_names(aml.leaderboard).base
    amodel = h2o.get_model(base_models[0])
    assert amodel.params['fold_column']['actual']['column_name'] == fold_column
    assert amodel.params['weights_column']['actual']['column_name'] == weights_column


def test_nfolds_default_and_fold_assignements_skipped_by_default():
    print("Check that fold assignments were skipped by default and nfolds > 1")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml_keep_cross_validation_fold_assignment_0",
                    nfolds=3, max_models=3, seed=1)
    aml.train(y=ds.target, training_frame=ds.train)
    base_models = get_partitioned_model_names(aml.leaderboard).base
    amodel = h2o.get_model(base_models[0])
    assert amodel.params['keep_cross_validation_fold_assignment']['actual'] == False
    assert amodel._model_json["output"]["cross_validation_fold_assignment_frame_id"] == None


def test_keep_cross_validation_fold_assignment_enabled_with_nfolds_neq_0():
    print("Check that fold assignments were kept when `keep_cross_validation_fold_assignment` = True and nfolds > 1")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml_keep_cross_validation_fold_assignment_1",
                    nfolds=3, max_models=3, seed=1,
                    keep_cross_validation_fold_assignment=True)
    aml.train(y=ds.target, training_frame=ds.train)
    base_models = get_partitioned_model_names(aml.leaderboard).base
    amodel = h2o.get_model(base_models[0])
    assert amodel.params['keep_cross_validation_fold_assignment']['actual'] == True
    assert amodel._model_json["output"]["cross_validation_fold_assignment_frame_id"] != None


def test_keep_cross_validation_fold_assignment_enabled_with_nfolds_eq_0():
    print("Check that fold assignments were skipped when `keep_cross_validation_fold_assignment` = True and nfolds = 0")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml_keep_cross_validation_fold_assignment_2",
                    nfolds=0, max_models=3, seed=1,
                    keep_cross_validation_fold_assignment=True)
    aml.train(y=ds.target, training_frame=ds.train)
    base_models = get_partitioned_model_names(aml.leaderboard).base
    amodel = h2o.get_model(base_models[0])
    assert amodel.params['keep_cross_validation_fold_assignment']['actual'] == False
    assert amodel._model_json["output"]["cross_validation_fold_assignment_frame_id"] == None


pu.run_tests([
    test_nfolds_param,
    test_nfolds_eq_0,
    test_fold_column,
    test_weights_column,
    test_fold_column_with_weights_column,
    test_nfolds_default_and_fold_assignements_skipped_by_default,
    test_keep_cross_validation_fold_assignment_enabled_with_nfolds_neq_0,
    test_keep_cross_validation_fold_assignment_enabled_with_nfolds_eq_0,
])
