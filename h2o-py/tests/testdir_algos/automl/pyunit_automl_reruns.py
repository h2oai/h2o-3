from __future__ import print_function
import os
import sys

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils as pu
from h2o.automl import H2OAutoML


max_models = 5


def import_dataset():
    df = h2o.import_file(path=pu.locate("smalldata/prostate/prostate.csv"))
    target = "CAPSULE"
    target_alt = "RACE"
    df[target] = df[target].asfactor()
    df[target_alt] = df[target_alt].asfactor()
    return pu.ns(train=df, target=target, target_alt=target_alt)


def model_names(lb):
    return lb[:, 0].as_data_frame().values.flatten()


def assert_same_leaderboard(lb1, lb2, size=0):
    print(lb1)
    assert len(lb1) == size
    print(lb2)
    assert len(lb2) == size
    assert all(m in lb2 for m in lb1)


def assert_distinct_leaderboard(lb1, lb2, size=0):
    print(lb1)
    assert len([x for x in lb1 if "Stacked" not in x]) == size
    print(lb2)
    assert len([x for x in lb2 if "Stacked" not in x]) == size
    assert not any(m in lb2 for m in lb1)


def assert_extended_leaderboard(lb1, lb2, size=0):
    print("size: {}".format(size))
    print(lb1)
    assert len([x for x in lb1 if "Stacked" not in x]) == size
    print(lb2)
    assert len([x for x in lb2 if "Stacked" not in x]) == size*2
    assert all(m in lb2 for m in lb1)


def suite_reruns_with_same_instance_without_project_name():

    def test_rerun_with_same_data_adds_models_to_leaderboard():
        ds = import_dataset()
        aml = H2OAutoML(max_models=max_models, seed=1, keep_cross_validation_predictions=True)
        aml.train(y=ds.target, training_frame=ds.train)
        project_name, lb1 = aml.project_name, model_names(aml.leaderboard)
        aml.train(y=ds.target, training_frame=ds.train)
        lb2 = model_names(aml.leaderboard)
        assert project_name == aml.project_name
        assert_extended_leaderboard(lb1, lb2, size=max_models)

    def test_rerun_with_different_predictors_adds_models_to_leaderboard():
        ds = import_dataset()
        aml = H2OAutoML(max_models=max_models, seed=1, keep_cross_validation_predictions=True)
        aml.train(y=ds.target, training_frame=ds.train)
        project_name, lb1 = aml.project_name, model_names(aml.leaderboard)
        aml.train(x=ds.train.columns[1:], y=ds.target, training_frame=ds.train)
        lb2 = model_names(aml.leaderboard)
        assert project_name == aml.project_name
        assert_extended_leaderboard(lb1, lb2, size=max_models)

    def test_rerun_with_different_training_frame_adds_models_to_leaderboard():
        ds = import_dataset()
        aml = H2OAutoML(max_models=max_models, seed=1, keep_cross_validation_predictions=True)
        aml.train(y=ds.target, training_frame=ds.train)
        project_name, lb1 = aml.project_name, model_names(aml.leaderboard)
        aml.train(y=ds.target, training_frame=ds.train[1:])
        lb2 = model_names(aml.leaderboard)
        assert project_name == aml.project_name
        assert_extended_leaderboard(lb1, lb2, size=max_models)

    def test_rerun_with_different_target_resets_leaderboard():
        ds = import_dataset()
        aml = H2OAutoML(max_models=max_models, seed=1, keep_cross_validation_predictions=True)
        aml.train(y=ds.target, training_frame=ds.train)
        project_name, lb1 = aml.project_name, model_names(aml.leaderboard)
        aml.train(y=ds.target_alt, training_frame=ds.train)
        lb2 = model_names(aml.leaderboard)
        assert project_name == aml.project_name
        assert_distinct_leaderboard(lb1, lb2, size=max_models)

    return [
        test_rerun_with_same_data_adds_models_to_leaderboard,
        test_rerun_with_different_predictors_adds_models_to_leaderboard,
        test_rerun_with_different_training_frame_adds_models_to_leaderboard,
        test_rerun_with_different_target_resets_leaderboard,
    ]


def suite_reruns_with_same_instance_with_project_name():

    def test_rerun_with_same_data_adds_models_to_leaderboard():
        ds = import_dataset()
        aml = H2OAutoML(project_name="test_automl_rerun", max_models=max_models, seed=1, keep_cross_validation_predictions=True)
        aml.train(y=ds.target, training_frame=ds.train)
        project_name, lb1 = aml.project_name, model_names(aml.leaderboard)
        aml.train(y=ds.target, training_frame=ds.train)
        lb2 = model_names(aml.leaderboard)
        assert project_name == aml.project_name
        assert_extended_leaderboard(lb1, lb2, size=max_models)

    def test_rerun_with_different_predictors_adds_models_to_leaderboard():
        ds = import_dataset()
        aml = H2OAutoML(project_name="test_automl_rerun", max_models=max_models, seed=1, keep_cross_validation_predictions=True)
        aml.train(y=ds.target, training_frame=ds.train)
        project_name, lb1 = aml.project_name, model_names(aml.leaderboard)
        aml.train(x=ds.train.columns[1:], y=ds.target, training_frame=ds.train)
        lb2 = model_names(aml.leaderboard)
        assert project_name == aml.project_name
        assert_extended_leaderboard(lb1, lb2, size=max_models)

    def test_rerun_with_different_training_frame_adds_models_to_leaderboard():
        ds = import_dataset()
        aml = H2OAutoML(project_name="test_automl_rerun", max_models=max_models, seed=1, keep_cross_validation_predictions=True)
        aml.train(y=ds.target, training_frame=ds.train)
        project_name, lb1 = aml.project_name, model_names(aml.leaderboard)
        aml.train(y=ds.target, training_frame=ds.train[1:])
        lb2 = model_names(aml.leaderboard)
        assert project_name == aml.project_name
        assert_extended_leaderboard(lb1, lb2, size=max_models)

    def test_rerun_with_different_target_resets_leaderboard():
        ds = import_dataset()
        aml = H2OAutoML(project_name="test_automl_rerun", max_models=max_models, seed=1, keep_cross_validation_predictions=True)
        aml.train(y=ds.target, training_frame=ds.train)
        project_name, lb1 = aml.project_name, model_names(aml.leaderboard)
        aml.train(y=ds.target_alt, training_frame=ds.train)
        lb2 = model_names(aml.leaderboard)
        assert project_name == aml.project_name
        assert_distinct_leaderboard(lb1, lb2, size=max_models)

    return [
        test_rerun_with_same_data_adds_models_to_leaderboard,
        test_rerun_with_different_predictors_adds_models_to_leaderboard,
        test_rerun_with_different_training_frame_adds_models_to_leaderboard,
        test_rerun_with_different_target_resets_leaderboard,
    ]


def suite_reruns_with_different_instance_without_project_name():

    def test_rerun_with_same_data_generates_distinct_leaderboard():
        ds = import_dataset()
        aml1 = H2OAutoML(max_models=max_models, seed=1, keep_cross_validation_predictions=True)
        aml1.train(y=ds.target, training_frame=ds.train)
        lb1 = model_names(aml1.leaderboard)
        aml2 = H2OAutoML(max_models=max_models, seed=1, keep_cross_validation_predictions=True)
        aml2.train(y=ds.target, training_frame=ds.train)
        lb2 = model_names(aml2.leaderboard)
        assert aml2.project_name != aml1.project_name
        assert_distinct_leaderboard(lb1, lb2, size=max_models)

    return [
        test_rerun_with_same_data_generates_distinct_leaderboard,
    ]


def suite_reruns_with_different_instances_same_project_name():

    def test_rerun_with_same_data_adds_models_to_leaderboard():
        ds = import_dataset()
        aml1 = H2OAutoML(project_name="test_automl_rerun", max_models=max_models, seed=1, keep_cross_validation_predictions=True)
        aml1.train(y=ds.target, training_frame=ds.train)
        lb1 = model_names(aml1.leaderboard)
        aml2 = H2OAutoML(project_name="test_automl_rerun", max_models=max_models, seed=1, keep_cross_validation_predictions=True)
        aml2.train(y=ds.target, training_frame=ds.train)
        lb2 = model_names(aml2.leaderboard)
        assert aml1.project_name == aml2.project_name
        assert_extended_leaderboard(lb1, lb2, size=max_models)

    def test_rerun_with_different_predictors_adds_models_to_leaderboard():
        ds = import_dataset()
        aml1 = H2OAutoML(project_name="test_automl_rerun", max_models=max_models, seed=1, keep_cross_validation_predictions=True)
        aml1.train(y=ds.target, training_frame=ds.train)
        lb1 = model_names(aml1.leaderboard)
        aml2 = H2OAutoML(project_name="test_automl_rerun", max_models=max_models, seed=1, keep_cross_validation_predictions=True)
        aml2.train(x=ds.train.columns[1:], y=ds.target, training_frame=ds.train)
        lb2 = model_names(aml2.leaderboard)
        assert aml1.project_name == aml2.project_name
        assert_extended_leaderboard(lb1, lb2, size=max_models)

    def test_rerun_with_different_training_frame_adds_models_to_leaderboard():
        ds = import_dataset()
        aml1 = H2OAutoML(project_name="test_automl_rerun", max_models=max_models, seed=1, keep_cross_validation_predictions=True)
        aml1.train(y=ds.target, training_frame=ds.train)
        lb1 = model_names(aml1.leaderboard)
        aml2 = H2OAutoML(project_name="test_automl_rerun", max_models=max_models, seed=1, keep_cross_validation_predictions=True)
        aml2.train(y=ds.target, training_frame=ds.train[1:])
        lb2 = model_names(aml2.leaderboard)
        assert aml1.project_name == aml2.project_name
        assert_extended_leaderboard(lb1, lb2, size=max_models)

    def test_rerun_with_different_target_resets_leaderboard():
        ds = import_dataset()
        aml1 = H2OAutoML(project_name="test_automl_rerun", max_models=max_models, seed=1, keep_cross_validation_predictions=True)
        aml1.train(y=ds.target, training_frame=ds.train)
        lb1 = model_names(aml1.leaderboard)
        aml2 = H2OAutoML(project_name="test_automl_rerun", max_models=max_models, seed=1, keep_cross_validation_predictions=True)
        aml2.train(y=ds.target_alt, training_frame=ds.train)
        lb2 = model_names(aml2.leaderboard)
        assert aml1.project_name == aml2.project_name
        assert_distinct_leaderboard(lb1, lb2, size=max_models)

    return [
        test_rerun_with_same_data_adds_models_to_leaderboard,
        test_rerun_with_different_predictors_adds_models_to_leaderboard,
        test_rerun_with_different_training_frame_adds_models_to_leaderboard,
        test_rerun_with_different_target_resets_leaderboard,
    ]


pu.run_tests([
    suite_reruns_with_same_instance_without_project_name(),
    suite_reruns_with_same_instance_with_project_name(),
    suite_reruns_with_different_instance_without_project_name(),
    suite_reruns_with_different_instances_same_project_name(),
])
