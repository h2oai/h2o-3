from __future__ import print_function
import sys, os, time
from itertools import cycle, islice
from random import uniform

from h2o.exceptions import H2OTypeError, H2OJobCancelled

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils as pu
from h2o.automl import H2OAutoML

"""
This test is used to check arguments passed into H2OAutoML along with different ways of using `.train()`
"""
max_models = 2


def import_dataset(seed=0, larger=False):
    df = h2o.import_file(path=pu.locate("smalldata/prostate/{}".format("prostate_complete.csv.zip" if larger else "prostate.csv")))
    target = "CAPSULE"
    df[target] = df[target].asfactor()
    #Split frames
    fr = df.split_frame(ratios=[.8,.1], seed=seed)
    #Set up train, validation, and test sets
    return dict(train=fr[0], valid=fr[1], test=fr[2], target=target, target_idx=1)


    # Below fails bc there are no models in the leaderboard, but AutoML needs to check the models to get the
    # model type (binomial, multinomial, or regression)
    # print("Check that exclude_algos implementation is complete, and empty leaderboard works")
    # aml = H2OAutoML(max_runtime_secs=30, project_name="py_aml0", stopping_rounds=3, stopping_tolerance=0.001, stopping_metric="AUC", max_models=max_models, seed=1234, exclude_algos=["GLM", "DRF", "GBM", "DeepLearning", "StackedEnsemble"])
    # aml.train(y="CAPSULE", training_frame=train)
    # print("Check leaderboard to ensure that it only has a header")
    # print(aml.leaderboard)
    # assert aml.leaderboard.nrows == 0, "with all algos excluded, leaderboard is not empty"

def get_partitioned_model_names(leaderboard):
    model_names = [leaderboard[i, 0] for i in range(0, (leaderboard.nrows))]
    se_model_names = [m for m in model_names if m.startswith('StackedEnsemble')]
    non_se_model_names = [m for m in model_names if m not in se_model_names]
    return model_names, non_se_model_names, se_model_names


def test_invalid_project_name():
    print("Check constructor raises error if project name is invalid")
    try:
        H2OAutoML(project_name="1nvalid")
    except Exception as e:
        assert "H2OAutoML" in str(e)
        assert "1nvalid" in str(e)


def test_early_stopping_defaults():
    print("Check default early stopping params")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml_early_stopping_defaults", max_models=max_models)
    aml.train(y=ds['target'], training_frame=ds['train'])
    stopping_criteria = aml._build_resp['build_control']['stopping_criteria']
    print(stopping_criteria)

    from math import sqrt
    auto_stopping_tolerance = (lambda fr: min(0.05, max(0.001, 1/sqrt((1 - sum(fr.nacnt()) / (fr.ncols * fr.nrows)) * fr.nrows))))(ds['train'])

    assert stopping_criteria['stopping_rounds'] == 3
    assert stopping_criteria['stopping_tolerance'] == auto_stopping_tolerance
    assert stopping_criteria['stopping_metric'] == 'AUTO'
    assert stopping_criteria['max_models'] == max_models
    assert stopping_criteria['max_runtime_secs'] == 0
    assert stopping_criteria['max_runtime_secs_per_model'] == 0



def test_early_stopping_args():
    print("Check arguments to H2OAutoML class")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml0", stopping_rounds=3, stopping_tolerance=0.001, stopping_metric="auc", max_models=max_models, seed=1234, exclude_algos=["DeepLearning"])
    aml.train(y=ds['target'], training_frame=ds['train'])
    assert aml.project_name == "py_aml0", "Project name is not set"
    assert aml.stopping_rounds == 3, "stopping_rounds is not set to 3"
    assert aml.stopping_tolerance == 0.001, "stopping_tolerance is not set to 0.001"
    assert aml.stopping_metric == "auc", "stopping_metrics is not set to `auc`"
    assert aml.max_models == 2, "max_models is not set to 2"
    assert aml.seed == 1234, "seed is not set to `1234`"
    print("Check leaderboard")
    print(aml.leaderboard)


def test_no_x_train_set_only():
    print("AutoML run with x not provided and train set only")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml1", stopping_rounds=3, stopping_tolerance=0.001, stopping_metric="AUC", max_models=max_models, seed=1234)
    aml.train(y=ds['target'], training_frame=ds['train'])
    assert aml.project_name == "py_aml1", "Project name is not set"
    assert aml.stopping_rounds == 3, "stopping_rounds is not set to 3"
    assert aml.stopping_tolerance == 0.001, "stopping_tolerance is not set to 0.001"
    assert aml.stopping_metric == "AUC", "stopping_metrics is not set to `AUC`"
    assert aml.max_models == 2, "max_models is not set to 2"
    assert aml.seed == 1234, "seed is not set to `1234`"
    print("Check leaderboard")
    print(aml.leaderboard)


def test_no_x_train_and_validation_sets():
    print("AutoML run with x not provided with train and valid")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml2", stopping_rounds=3, stopping_tolerance=0.001, stopping_metric="AUC", max_models=max_models, seed=1234)
    aml.train(y=ds['target'], training_frame=ds['train'], validation_frame=ds['valid'])
    assert aml.project_name == "py_aml2", "Project name is not set"
    assert aml.stopping_rounds == 3, "stopping_rounds is not set to 3"
    assert aml.stopping_tolerance == 0.001, "stopping_tolerance is not set to 0.001"
    assert aml.stopping_metric == "AUC", "stopping_metrics is not set to `AUC`"
    assert aml.max_models == 2, "max_models is not set to 2"
    assert aml.seed == 1234, "seed is not set to `1234`"
    log_df = aml.event_log.as_data_frame()
    warn_messages = log_df[log_df['level'] == 'Warn']['message']
    assert warn_messages.str.startswith("User specified a validation frame with cross-validation still enabled").any(), \
        "a warning should have been raised for using a validation frame with CV enabled"
    print("Check leaderboard")
    print(aml.leaderboard)


def test_no_x_train_and_test_sets():
    print("AutoML run with x not provided with train and test")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml3", stopping_rounds=3, stopping_tolerance=0.001, stopping_metric="AUC", max_models=max_models, seed=1234)
    aml.train(y=ds['target'], training_frame=ds['train'], leaderboard_frame=ds['test'])
    assert aml.project_name == "py_aml3", "Project name is not set"
    assert aml.stopping_rounds == 3, "stopping_rounds is not set to 3"
    assert aml.stopping_tolerance == 0.001, "stopping_tolerance is not set to 0.001"
    assert aml.stopping_metric == "AUC", "stopping_metrics is not set to `AUC`"
    assert aml.max_models == 2, "max_models is not set to 2"
    assert aml.seed == 1234, "seed is not set to `1234`"
    print("Check leaderboard")
    print(aml.leaderboard)


def test_no_x_train_and_validation_and_test_sets():
    print("AutoML run with x not provided with train, valid, and test")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml4", stopping_rounds=3, stopping_tolerance=0.001, stopping_metric="AUC", max_models=max_models, seed=1234, nfolds=0)
    aml.train(y=ds['target'], training_frame=ds['train'], validation_frame=ds['valid'], leaderboard_frame=ds['test'])
    assert aml.project_name == "py_aml4", "Project name is not set"
    assert aml.stopping_rounds == 3, "stopping_rounds is not set to 3"
    assert aml.stopping_tolerance == 0.001, "stopping_tolerance is not set to 0.001"
    assert aml.stopping_metric == "AUC", "stopping_metrics is not set to `AUC`"
    assert aml.max_models == 2, "max_models is not set to 2"
    assert aml.seed == 1234, "seed is not set to `1234`"
    log_df = aml.event_log.as_data_frame()
    warn_messages = log_df[log_df['level'] == 'Warn']['message']
    assert not warn_messages.str.startswith("User specified a validation frame with cross-validation still enabled").any(), \
        "no warning should have been raised as CV was disabled"
    print("Check leaderboard")
    print(aml.leaderboard)


def test_no_x_y_as_idx_train_and_validation_and_test_sets():
    print("AutoML run with x not provided and y as col idx with train, valid, and test")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml5", stopping_rounds=3, stopping_tolerance=0.001, stopping_metric="AUC", max_models=max_models, seed=1234)
    aml.train(y=ds['target_idx'], training_frame=ds['train'], validation_frame=ds['valid'], leaderboard_frame=ds['test'])
    assert aml.project_name == "py_aml5", "Project name is not set"
    assert aml.stopping_rounds == 3, "stopping_rounds is not set to 3"
    assert aml.stopping_tolerance == 0.001, "stopping_tolerance is not set to 0.001"
    assert aml.stopping_metric == "AUC", "stopping_metrics is not set to `AUC`"
    assert aml.max_models == 2, "max_models is not set to 2"
    assert aml.seed == 1234, "seed is not set to `1234`"
    print("Check leaderboard")
    print(aml.leaderboard)


def test_predict_on_train_set():
    print("Check predict, leader, and leaderboard")
    print("AutoML run with x not provided and train set only")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml6", stopping_rounds=3, stopping_tolerance=0.001, stopping_metric="AUC", max_models=max_models, seed=1234)
    aml.train(y=ds['target'], training_frame=ds['train'])
    print("Check leaderboard")
    print(aml.leaderboard)
    print("Check predictions")
    print(aml.predict(ds['train']))
    

def test_nfolds_param():
    print("Check nfolds is passed through to base models")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml_nfolds3", nfolds=3, max_models=3, seed=1)
    aml.train(y=ds['target'], training_frame=ds['train'])
    _, non_se, _ = get_partitioned_model_names(aml.leaderboard)
    amodel = h2o.get_model(non_se[0])
    assert amodel.params['nfolds']['actual'] == 3


def test_nfolds_eq_0():
    print("Check nfolds = 0 works properly")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml_nfolds0", nfolds=0, max_models=3, seed=1)
    aml.train(y=ds['target'], training_frame=ds['train'])
    _, non_se, _ = get_partitioned_model_names(aml.leaderboard)
    amodel = h2o.get_model(non_se[0])
    assert amodel.params['nfolds']['actual'] == 0


def test_fold_column():
    print("Check fold_column param")
    ds = import_dataset()
    fold_column = "fold_id"
    nrows = ds['train'].nrows
    train = ds['train'].concat(h2o.H2OFrame(list(islice(cycle(range(3)), 0, nrows)), column_names=[fold_column]))
    aml = H2OAutoML(project_name="py_aml_fold_column", max_models=3, seed=1, keep_cross_validation_models=True)
    aml.train(y=ds['target'], training_frame=train, fold_column=fold_column)
    _, non_se, se = get_partitioned_model_names(aml.leaderboard)
    amodel = h2o.get_model(non_se[0])
    assert amodel.params['fold_column']['actual']['column_name'] == fold_column
    ensemble = h2o.get_model(se[0])
    metalearner = h2o.get_model(ensemble.metalearner()['name'])
    assert metalearner.params['fold_column']['actual']['column_name'] == fold_column
    assert len(metalearner.cross_validation_models()) == 3


def test_weights_column():
    print("Check weights_column")
    ds = import_dataset()
    nrows = ds['train'].nrows
    weights_column = "weight"
    train = ds['train'].concat(h2o.H2OFrame(list(map(lambda _: uniform(0, 5), range(nrows))), column_names=[weights_column]))
    aml = H2OAutoML(project_name="py_aml_weights_column", max_models=3, seed=1)
    aml.train(y=ds['target'], training_frame=train, weights_column=weights_column)
    _, non_se, se = get_partitioned_model_names(aml.leaderboard)
    amodel = h2o.get_model(non_se[0])
    assert amodel.params['weights_column']['actual']['column_name'] == weights_column


def test_fold_column_with_weights_column():
    print("Check fold_column and weights_column")
    ds = import_dataset()
    fold_column = "fold_id"
    weights_column = "weight"
    nrows = ds['train'].nrows
    train = (ds['train']
             .concat(h2o.H2OFrame(list(islice(cycle(range(3)), 0, nrows)), column_names=[fold_column]))
             .concat(h2o.H2OFrame(list(map(lambda _: uniform(0, 5), range(nrows))), column_names=[weights_column])))
    aml = H2OAutoML(project_name="py_aml_weights_column", max_models=3, seed=1)
    aml.train(y=ds['target'], training_frame=train, fold_column=fold_column, weights_column=weights_column)
    _, non_se, se = get_partitioned_model_names(aml.leaderboard)
    amodel = h2o.get_model(non_se[0])
    assert amodel.params['fold_column']['actual']['column_name'] == fold_column
    assert amodel.params['weights_column']['actual']['column_name'] == weights_column


def test_balance_classes():
    print("Check balance_classes & related args work properly")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml_balance_classes_etc",
                    exclude_algos=['XGBoost'],  # XGB doesn't support balance_classes
                    max_models=3,
                    balance_classes=True,
                    class_sampling_factors=[0.2, 1.4],
                    max_after_balance_size=3.0,
                    seed=1)
    aml.train(y=ds['target'], training_frame=ds['train'])
    _, non_se, _ = get_partitioned_model_names(aml.leaderboard)
    amodel = h2o.get_model(non_se[0])
    assert amodel.params['balance_classes']['actual'] == True
    assert amodel.params['max_after_balance_size']['actual'] == 3.0
    assert amodel.params['class_sampling_factors']['actual'] == [0.2, 1.4]


def test_nfolds_default_and_fold_assignements_skipped_by_default():
    print("Check that fold assignments were skipped by default and nfolds > 1")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml_keep_cross_validation_fold_assignment_0",
                    nfolds=3, max_models=3, seed=1)
    aml.train(y=ds['target'], training_frame=ds['train'])
    _, non_se, _ = get_partitioned_model_names(aml.leaderboard)
    amodel = h2o.get_model(non_se[0])
    assert amodel.params['keep_cross_validation_fold_assignment']['actual'] == False
    assert amodel._model_json["output"]["cross_validation_fold_assignment_frame_id"] == None


def test_keep_cross_validation_fold_assignment_enabled_with_nfolds_neq_0():
    print("Check that fold assignments were kept when `keep_cross_validation_fold_assignment` = True and nfolds > 1")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml_keep_cross_validation_fold_assignment_1",
                    nfolds=3, max_models=3, seed=1,
                    keep_cross_validation_fold_assignment=True)
    aml.train(y=ds['target'], training_frame=ds['train'])
    _, non_se, _ = get_partitioned_model_names(aml.leaderboard)
    amodel = h2o.get_model(non_se[0])
    assert amodel.params['keep_cross_validation_fold_assignment']['actual'] == True
    assert amodel._model_json["output"]["cross_validation_fold_assignment_frame_id"] != None


def test_keep_cross_validation_fold_assignment_enabled_with_nfolds_eq_0():
    print("Check that fold assignments were skipped when `keep_cross_validation_fold_assignment` = True and nfolds = 0")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml_keep_cross_validation_fold_assignment_2",
                    nfolds=0, max_models=3, seed=1,
                    keep_cross_validation_fold_assignment=True)
    aml.train(y=ds['target'], training_frame=ds['train'])
    _, non_se, _ = get_partitioned_model_names(aml.leaderboard)
    amodel = h2o.get_model(non_se[0])
    assert amodel.params['keep_cross_validation_fold_assignment']['actual'] == False
    assert amodel._model_json["output"]["cross_validation_fold_assignment_frame_id"] == None


def test_stacked_ensembles_are_trained_after_timeout():
    print("Check that Stacked Ensembles are still trained after timeout")
    max_runtime_secs = 10
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml_SE_after_timeout", seed=1, max_runtime_secs=max_runtime_secs, exclude_algos=['XGBoost', 'DeepLearning'])
    start = time.time()
    aml.train(y=ds['target'], training_frame=ds['train'])
    end = time.time()
    assert end-start - max_runtime_secs > 0

    _, _, se = get_partitioned_model_names(aml.leaderboard)
    assert len(se) > 0, "StackedEnsemble should still be trained after timeout"  # we don't need to test if all SEs are built, there may be only one if just one model type was built.


def test_automl_stops_after_max_models():
    print("Check that automl gets interrupted after `max_models`")
    ds = import_dataset()
    max_models = 5
    aml = H2OAutoML(project_name="py_aml_max_models", seed=1, max_models=max_models)
    aml.train(y=ds['target'], training_frame=ds['train'])

    _, non_se, _ = get_partitioned_model_names(aml.leaderboard)
    assert len(non_se) == max_models, "obtained {} base models when {} are expected".format(len(non_se), max_models)


def test_stacked_ensembles_are_trained_after_max_models():
    print("Check that Stacked Ensembles are still trained after max models have been trained")
    max_models = 5
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml_SE_after_max_models", seed=1, max_models=max_models)
    aml.train(y=ds['target'], training_frame=ds['train'])

    _, _, se = get_partitioned_model_names(aml.leaderboard)
    assert len(se) == 2, "StackedEnsemble should still be trained after max models have been reached"


def test_stacked_ensembles_are_trained_with_blending_frame_even_if_nfolds_eq_0():
    print("Check that we can disable cross-validation when passing a blending frame and that Stacked Ensembles are trained using this frame.")
    max_models = 5
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml_blending_frame", seed=1, max_models=max_models, nfolds=0)
    aml.train(y=ds['target'], training_frame=ds['train'], blending_frame=ds['valid'], leaderboard_frame=ds['test'])

    _, _, se = get_partitioned_model_names(aml.leaderboard)
    assert len(se) == 2, "In blending mode, StackedEnsemble should still be trained in spite of nfolds=0."
    for m in se:
        model = h2o.get_model(m)
        assert model.params['blending_frame']['actual']['name'] == ds['valid'].frame_id
        assert model._model_json['output']['stacking_strategy'] == 'blending'


def test_frames_cannot_be_passed_as_key():
    print("Check that all AutoML frames can be passed as keys.")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml_frames_as_keys", seed=1, max_models=3, nfolds=0)

    kw_args = [
        dict(training_frame=ds['train'].frame_id),
        dict(training_frame=ds['train'], validation_frame=ds['valid'].frame_id),
        dict(training_frame=ds['train'], blending_frame=ds['valid'].frame_id),
        dict(training_frame=ds['train'], leaderboard_frame=ds['test'].frame_id),
    ]
    for kwargs in kw_args:
        try:
            aml.train(y=ds['target'], **kwargs)
            assert False, "should have thrown due to wrong frame key"
        except H2OTypeError as e:
            attr = next(k for k, v in kwargs.items() if v is not ds['train'])
            assert "'{}' must be a valid H2OFrame".format(attr) in str(e)


def test_no_time_limit_if_max_models_is_provided():
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_no_time_limit", seed=1, max_models=1)
    aml.train(y=ds['target'], training_frame=ds['train'])
    max_runtime = aml._build_resp['build_control']['stopping_criteria']['max_runtime_secs']
    max_models = aml._build_resp['build_control']['stopping_criteria']['max_models']
    assert max_models == 1, max_models
    assert max_runtime == 0, max_runtime


def test_max_runtime_secs_alone():
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_max_runtime_secs", seed=1, max_runtime_secs=7)
    aml.train(y=ds['target'], training_frame=ds['train'])
    max_runtime = aml._build_resp['build_control']['stopping_criteria']['max_runtime_secs']
    max_models = aml._build_resp['build_control']['stopping_criteria']['max_models']
    assert max_runtime == 7
    assert max_models == 0


def test_max_runtime_secs_can_be_set_in_combination_with_max_models_and_max_models_wins():
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_all_stopping_constraints", seed=1, max_models=1, max_runtime_secs=1200)
    aml.train(y=ds['target'], training_frame=ds['train'])
    max_runtime = aml._build_resp['build_control']['stopping_criteria']['max_runtime_secs']
    max_models = aml._build_resp['build_control']['stopping_criteria']['max_models']
    assert max_runtime == 1200
    assert max_models == 1
    assert aml.leaderboard.nrows == 1
    assert int(aml.training_info['duration_secs']) < max_runtime/2  # being generous to avoid errors on slow Jenkins


def test_max_runtime_secs_can_be_set_in_combination_with_max_models_and_max_runtime_wins():
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_all_stopping_constraints", seed=1, max_models=20, max_runtime_secs=12)
    aml.train(y=ds['target'], training_frame=ds['train'])
    max_runtime = aml._build_resp['build_control']['stopping_criteria']['max_runtime_secs']
    max_models = aml._build_resp['build_control']['stopping_criteria']['max_models']
    assert max_runtime == 12
    assert max_models == 20
    assert aml.leaderboard.nrows < 20
    assert int(aml.training_info['duration_secs']) < 2*max_runtime  # being generous to avoid errors on slow Jenkins


def test_default_max_runtime_if_no_max_models_provided():
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_no_stopping_constraints", seed=1, verbosity='Info')
    with pu.Timeout(5, on_timeout=lambda: aml._job.cancel()):
        try:
            aml.train(y=ds['target'], training_frame=ds['train'])
        except H2OJobCancelled:
            pass
        max_runtime = aml._build_resp['build_control']['stopping_criteria']['max_runtime_secs']
        max_models = aml._build_resp['build_control']['stopping_criteria']['max_models']
        assert max_runtime == 3600
        assert max_models == 0


pu.run_tests([
    test_invalid_project_name,
    test_early_stopping_defaults,
    test_early_stopping_args,
    test_no_x_train_set_only,
    test_no_x_train_and_validation_sets,
    test_no_x_train_and_test_sets,
    test_no_x_train_and_validation_and_test_sets,
    test_no_x_y_as_idx_train_and_validation_and_test_sets,
    test_predict_on_train_set,
    test_nfolds_param,
    test_nfolds_eq_0,
    test_fold_column,
    test_weights_column,
    test_fold_column_with_weights_column,
    test_balance_classes,
    test_nfolds_default_and_fold_assignements_skipped_by_default,
    test_keep_cross_validation_fold_assignment_enabled_with_nfolds_neq_0,
    test_keep_cross_validation_fold_assignment_enabled_with_nfolds_eq_0,
    test_stacked_ensembles_are_trained_after_timeout,
    test_automl_stops_after_max_models,
    test_stacked_ensembles_are_trained_after_max_models,
    test_stacked_ensembles_are_trained_with_blending_frame_even_if_nfolds_eq_0,
    test_frames_cannot_be_passed_as_key,
    test_no_time_limit_if_max_models_is_provided,
    test_max_runtime_secs_alone,
    test_max_runtime_secs_can_be_set_in_combination_with_max_models_and_max_models_wins,
    test_max_runtime_secs_can_be_set_in_combination_with_max_models_and_max_runtime_wins,
    test_default_max_runtime_if_no_max_models_provided,
])
