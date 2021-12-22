from __future__ import print_function
import sys, os

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from h2o.automl import H2OAutoML
from tests import pyunit_utils as pu

from _automl_utils import import_dataset


def check_ignore_cols_automl(models,names,x,y):
    models = sum(models.as_data_frame().values.tolist(),[])
    for model in models:
        if "StackedEnsemble" in model:
            continue
        else:
            assert set(h2o.get_model(model).params["ignored_columns"]["actual"]) == set(names) - {y} - set(x), \
                "ignored columns are not honored for model " + model


def test_columns_not_in_x_and_y_are_ignored():
    ds = import_dataset()
    names = ds.train.names
    x = ["AGE", "RACE", "DPROS"]
    y = ds.target

    def test_with_x_y_as_str_list():
        aml = H2OAutoML(max_models=2, stopping_rounds=3, stopping_tolerance=0.001)
        aml.train(x=x, y=y, training_frame=ds.train, validation_frame=ds.valid, leaderboard_frame=ds.test)
        print("AutoML leaderboard")
        print(aml.leaderboard)
        models = aml.leaderboard["model_id"]
        check_ignore_cols_automl(models, names, x, y)

    def test_with_x_y_as_indices():
        aml = H2OAutoML(max_models=2, stopping_rounds=3, stopping_tolerance=0.001)
        aml.train(x=[2, 3, 4], y=1, training_frame=ds.train, validation_frame=ds.valid, leaderboard_frame=ds.test)
        print("AutoML leaderboard")
        print(aml.leaderboard)
        models = aml.leaderboard["model_id"]
        check_ignore_cols_automl(models, names, x, y)

    def test_with_x_as_str_list_y_as_index():
        aml = H2OAutoML(max_models=2, stopping_rounds=3, stopping_tolerance=0.001)
        aml.train(x=x, y=1, training_frame=ds.train, validation_frame=ds.valid, leaderboard_frame=ds.test)
        print("AutoML leaderboard")
        print(aml.leaderboard)
        models = aml.leaderboard["model_id"]
        check_ignore_cols_automl(models, names, x, y)

    def test_with_x_indices_y_as_str():
        aml = H2OAutoML(max_models=2, stopping_rounds=3, stopping_tolerance=0.001)
        aml.train(x=[2,3,4], y=y, training_frame=ds.train, validation_frame=ds.valid, leaderboard_frame=ds.test)
        print("AutoML leaderboard")
        print(aml.leaderboard)
        models = aml.leaderboard["model_id"]
        check_ignore_cols_automl(models, names, x, y)
    
    pu.run_tests([
        test_with_x_y_as_str_list,
        test_with_x_y_as_indices,
        test_with_x_as_str_list_y_as_index,
        test_with_x_indices_y_as_str
    ], run_in_isolation=False)


pu.run_tests([
    test_columns_not_in_x_and_y_are_ignored
])
