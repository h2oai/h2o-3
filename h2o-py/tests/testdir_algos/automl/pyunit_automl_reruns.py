from __future__ import print_function
import sys, os, time

from h2o.exceptions import H2OTypeError

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils as pu
from h2o.automl import H2OAutoML


def import_dataset():
    df = h2o.import_file(path=pu.locate("smalldata/prostate/prostate.csv"))
    target = "CAPSULE"
    df[target] = df[target].asfactor()
    return pu.ns(train=df, target=target)


def suite_reruns_with_same_instance_without_project_name():

    def test_rerun_with_same_data_adds_models_to_leaderboard():
        ds = import_dataset()
        aml = H2OAutoML(max_models=2, seed=1, keep_cross_validation_predictions=True)
        aml.train(y=ds.target, training_frame=ds.train)
        project_name = aml.project_name
        print(aml.leaderboard)
        assert aml.leaderboard.nrows == 4
        aml.train(y=ds.target, training_frame=ds.train)
        print(aml.leaderboard)
        assert aml.leaderboard.nrows == 8
        assert project_name == aml.project_name


    def test_rerun_with_different_predictors_adds_models_to_leaderboard():
        ds = import_dataset()
        aml = H2OAutoML(max_models=2, seed=1, keep_cross_validation_predictions=True)
        aml.train(y=ds.target, training_frame=ds.train)
        project_name = aml.project_name
        print(aml.leaderboard)
        assert aml.leaderboard.nrows == 4
        aml.train(x=ds.train.columns[1:], y=ds.target, training_frame=ds.train)
        print(aml.leaderboard)
        assert aml.leaderboard.nrows == 8
        assert project_name == aml.project_name

    def test_rerun_with_different_training_frame_resets_leaderboard():
        ds = import_dataset()
        aml = H2OAutoML(max_models=2, seed=1, keep_cross_validation_predictions=True)
        aml.train(y=ds.target, training_frame=ds.train)
        project_name = aml.project_name
        print(aml.leaderboard)
        assert aml.leaderboard.nrows == 4
        aml.train(y=ds.target, training_frame=ds.train[1:])
        print(aml.leaderboard)
        assert aml.leaderboard.nrows == 4
        assert project_name == aml.project_name

    def test_rerun_with_different_target_resets_leaderboard():
        pass

    return [
        test_rerun_with_same_data_adds_models_to_leaderboard,
        test_rerun_with_different_predictors_adds_models_to_leaderboard,
        test_rerun_with_different_training_frame_resets_leaderboard,
        test_rerun_with_different_target_resets_leaderboard,
    ]


def suite_reruns_with_same_instance_with_project_name():

    def test_rerun_with_same_data_adds_models_to_leaderboard():
        pass

    def test_rerun_with_different_predictors_adds_models_to_leaderboard():
        pass

    def test_rerun_with_different_training_frame_resets_leaderboard():
        pass

    def test_rerun_with_different_target_resets_leaderboard():
        pass

    return [
        test_rerun_with_same_data_adds_models_to_leaderboard,
        test_rerun_with_different_predictors_adds_models_to_leaderboard,
        test_rerun_with_different_training_frame_resets_leaderboard,
        test_rerun_with_different_target_resets_leaderboard,
    ]


def suite_reruns_with_different_instance_without_project_name():

    def test_rerun_with_same_data_generates_distinct_leaderboard():
        pass

    return [
        test_rerun_with_same_data_generates_distinct_leaderboard,
    ]


def suite_reruns_with_different_instances_same_project_name():

    def test_rerun_with_same_data_adds_models_to_leaderboard():
        pass

    def test_rerun_with_different_predictors_adds_models_to_leaderboard():
        pass

    def test_rerun_with_different_training_frame_resets_leaderboard():
        pass

    def test_rerun_with_different_target_resets_leaderboard():
        pass

    return [
        test_rerun_with_same_data_adds_models_to_leaderboard,
        test_rerun_with_different_predictors_adds_models_to_leaderboard,
        test_rerun_with_different_training_frame_resets_leaderboard,
        test_rerun_with_different_target_resets_leaderboard,
    ]


pu.run_tests([
    suite_reruns_with_same_instance_without_project_name(),
    suite_reruns_with_same_instance_with_project_name(),
    suite_reruns_with_different_instance_without_project_name(),
    suite_reruns_with_different_instances_same_project_name(),
])
