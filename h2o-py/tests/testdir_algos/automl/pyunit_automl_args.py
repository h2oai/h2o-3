from __future__ import print_function
import sys, os, time
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.automl import H2OAutoML

"""
This test is used to check arguments passed into H2OAutoML along with different ways of using `.train()`
"""
max_models = 2

def import_dataset():
    df = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    target = "CAPSULE"
    df[target] = df[target].asfactor()
    #Split frames
    fr = df.split_frame(ratios=[.8,.1])
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

def test_early_stopping_args():
    print("Check arguments to H2OAutoML class")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml0", stopping_rounds=3, stopping_tolerance=0.001, stopping_metric="AUC", max_models=max_models, seed=1234, exclude_algos=["DeepLearning"])
    aml.train(y=ds['target'], training_frame=ds['train'])
    assert aml.project_name == "py_aml0", "Project name is not set"
    assert aml.stopping_rounds == 3, "stopping_rounds is not set to 3"
    assert aml.stopping_tolerence == 0.001, "stopping_tolerance is not set to 0.001"
    assert aml.stopping_metric == "AUC", "stopping_metrics is not set to `AUC`"
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
    assert aml.stopping_tolerence == 0.001, "stopping_tolerance is not set to 0.001"
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
    assert aml.stopping_tolerence == 0.001, "stopping_tolerance is not set to 0.001"
    assert aml.stopping_metric == "AUC", "stopping_metrics is not set to `AUC`"
    assert aml.max_models == 2, "max_models is not set to 2"
    assert aml.seed == 1234, "seed is not set to `1234`"
    print("Check leaderboard")
    print(aml.leaderboard)


def test_no_x_train_and_test_sets():
    print("AutoML run with x not provided with train and test")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml3", stopping_rounds=3, stopping_tolerance=0.001, stopping_metric="AUC", max_models=max_models, seed=1234)
    aml.train(y=ds['target'], training_frame=ds['train'], leaderboard_frame=ds['test'])
    assert aml.project_name == "py_aml3", "Project name is not set"
    assert aml.stopping_rounds == 3, "stopping_rounds is not set to 3"
    assert aml.stopping_tolerence == 0.001, "stopping_tolerance is not set to 0.001"
    assert aml.stopping_metric == "AUC", "stopping_metrics is not set to `AUC`"
    assert aml.max_models == 2, "max_models is not set to 2"
    assert aml.seed == 1234, "seed is not set to `1234`"
    print("Check leaderboard")
    print(aml.leaderboard)


def test_no_x_train_and_validation_and_test_sets():
    print("AutoML run with x not provided with train, valid, and test")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml4", stopping_rounds=3, stopping_tolerance=0.001, stopping_metric="AUC", max_models=max_models, seed=1234)
    aml.train(y=ds['target'], training_frame=ds['train'], validation_frame=ds['valid'], leaderboard_frame=ds['test'])
    assert aml.project_name == "py_aml4", "Project name is not set"
    assert aml.stopping_rounds == 3, "stopping_rounds is not set to 3"
    assert aml.stopping_tolerence == 0.001, "stopping_tolerance is not set to 0.001"
    assert aml.stopping_metric == "AUC", "stopping_metrics is not set to `AUC`"
    assert aml.max_models == 2, "max_models is not set to 2"
    assert aml.seed == 1234, "seed is not set to `1234`"
    print("Check leaderboard")
    print(aml.leaderboard)


def test_no_x_y_as_idx_train_and_validation_and_test_sets():
    print("AutoML run with x not provided and y as col idx with train, valid, and test")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml5", stopping_rounds=3, stopping_tolerance=0.001, stopping_metric="AUC", max_models=max_models, seed=1234)
    aml.train(y=ds['target_idx'], training_frame=ds['train'], validation_frame=ds['valid'], leaderboard_frame=ds['test'])
    assert aml.project_name == "py_aml5", "Project name is not set"
    assert aml.stopping_rounds == 3, "stopping_rounds is not set to 3"
    assert aml.stopping_tolerence == 0.001, "stopping_tolerance is not set to 0.001"
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


def test_balance_classes():
    print("Check balance_classes & related args work properly")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml_balance_classes_etc", max_models=3,
                    balance_classes=True, class_sampling_factors=[0.2, 1.4], max_after_balance_size=3.0, seed=1)
    aml.train(y=ds['target'], training_frame=ds['train'])
    _, non_se, _ = get_partitioned_model_names(aml.leaderboard)
    amodel = h2o.get_model(non_se[0])
    assert amodel.params['balance_classes']['actual'] == True
    assert amodel.params['max_after_balance_size']['actual'] == 3.0
    assert amodel.params['class_sampling_factors']['actual'] == [0.2, 1.4]


def test_nfolds_default_and_fold_assignements_skipped_by_default():
    print("Check that fold assignments were skipped by default and nfolds > 1")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml_keep_cross_validation_fold_assignment0",
                    nfolds=3, max_models=3, seed=1)
    aml.train(y=ds['target'], training_frame=ds['train'])
    amodel = h2o.get_model(aml.leaderboard[aml.leaderboard.nrows-1,0])
    assert amodel.params['keep_cross_validation_fold_assignment']['actual'] == False
    assert amodel._model_json["output"]["cross_validation_fold_assignment_frame_id"] == None


def test_keep_cross_validation_fold_assignment_enabled_with_nfolds_neq_0():
    print("Check that fold assignments were kept when `keep_cross_validation_fold_assignment` = True and nfolds > 1")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml_keep_cross_validation_fold_assignment1",
                    nfolds=3, max_models=3, seed=1,
                    keep_cross_validation_fold_assignment=True)
    aml.train(y=ds['target'], training_frame=ds['train'])
    amodel = h2o.get_model(aml.leaderboard[aml.leaderboard.nrows-1,0])
    assert amodel.params['keep_cross_validation_fold_assignment']['actual'] == True
    assert amodel._model_json["output"]["cross_validation_fold_assignment_frame_id"] != None


def test_keep_cross_validation_fold_assignment_enabled_with_nfolds_eq_0():
    print("Check that fold assignments were skipped when `keep_cross_validation_fold_assignment` = True and nfolds = 0")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml_keep_cross_validation_fold_assignment2",
                    nfolds=0, max_models=3, seed=1,
                    keep_cross_validation_fold_assignment=True)
    aml.train(y=ds['target'], training_frame=ds['train'])
    amodel = h2o.get_model(aml.leaderboard[aml.leaderboard.nrows-1,0])
    assert amodel.params['keep_cross_validation_fold_assignment']['actual'] == False
    assert amodel._model_json["output"]["cross_validation_fold_assignment_frame_id"] == None


def test_automl_stops_after_max_runtime_secs():
    print("Check that automl gets interrupted after `max_runtime_secs`")
    max_runtime_secs = 30
    cancel_tolerance_secs = 5+5   # should work for most cases given current mechanism, +5 due to SE which currently ignore max_runtime_secs
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml_max_runtime_secs", seed=1, max_runtime_secs=max_runtime_secs)
    start = time.time()
    aml.train(y=ds['target'], training_frame=ds['train'])
    end = time.time()
    assert abs(end-start - max_runtime_secs) < cancel_tolerance_secs, end-start


def test_stacked_ensembles_are_trained_after_timeout():
    print("Check that Stacked Ensembles are still trained after timeout")
    max_runtime_secs = 10
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml_SE_after_timeout", seed=1, max_runtime_secs=max_runtime_secs, exclude_algos=['DeepLearning'])
    start = time.time()
    aml.train(y=ds['target'], training_frame=ds['train'])
    end = time.time()
    assert end-start - max_runtime_secs > 0

    stacked_ensembles = [m for m in [aml.leaderboard[i, 0] for i in range(0, (aml.leaderboard.nrows))] if m.startswith('StackedEnsemble')]
    assert len(stacked_ensembles) == 2, "StackedEnsemble should still be trained after timeout"


def test_automl_stops_after_max_models():
    print("Check that automl gets interrupted after `max_models`")
    ds = import_dataset()
    max_models = 5
    aml = H2OAutoML(project_name="py_aml_max_models", seed=1, max_models=max_models)
    aml.train(y=ds['target'], training_frame=ds['train'])

    base_models = [m for m in [aml.leaderboard[i, 0] for i in range(0, (aml.leaderboard.nrows))] if not m.startswith('StackedEnsemble')]
    assert len(base_models) == max_models, "obtained {} base models when {} are expected".format(len(base_models), max_models)


def test_stacked_ensembles_are_trained_after_max_models():
    print("Check that Stacked Ensembles are still trained after max models have been trained")
    max_models = 5
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml_SE_after_max_models", seed=1, max_models=max_models)
    aml.train(y=ds['target'], training_frame=ds['train'])

    stacked_ensembles = [m for m in [aml.leaderboard[i, 0] for i in range(0, (aml.leaderboard.nrows))] if m.startswith('StackedEnsemble')]
    assert len(stacked_ensembles) == 2, "StackedEnsemble should still be trained after max models have been reached"



    # TO DO  PUBDEV-5676
    # Add a test that checks fold_column like in runit


tests = [
    test_early_stopping_args,
    test_no_x_train_set_only,
    test_no_x_train_and_validation_sets,
    test_no_x_train_and_test_sets,
    test_no_x_train_and_validation_and_test_sets,
    test_no_x_y_as_idx_train_and_validation_and_test_sets,
    test_predict_on_train_set,
    test_nfolds_param,
    test_nfolds_eq_0,
    test_balance_classes,
    test_nfolds_default_and_fold_assignements_skipped_by_default,
    test_keep_cross_validation_fold_assignment_enabled_with_nfolds_neq_0,
    test_keep_cross_validation_fold_assignment_enabled_with_nfolds_eq_0,
    test_automl_stops_after_max_runtime_secs,
    test_stacked_ensembles_are_trained_after_timeout,
    test_automl_stops_after_max_models,
    test_stacked_ensembles_are_trained_after_max_models,
]

if __name__ == "__main__":
    for test in tests: pyunit_utils.standalone_test(test)
else:
    for test in tests: test()
