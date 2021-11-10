from __future__ import print_function
import sys, os

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from h2o.automl import H2OAutoML
from tests import pyunit_utils as pu

pu.load_module("_automl_utils", os.path.join(os.path.dirname(__file__)))
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
    #Use same project_name so we add to leaderboard for each run
    aml = H2OAutoML(max_models=2, stopping_rounds=3, stopping_tolerance=0.001, project_name="aml1")

    print("AutoML with x as a str list, train, valid, and test")
    x = ["AGE", "RACE", "DPROS"]
    y = ds.target
    names = ds.train.names
    aml.train(x=x, y=y, training_frame=ds.train, validation_frame=ds.valid, leaderboard_frame=ds.test)
    print("AutoML leaderboard")
    print(aml.leaderboard)
    models = aml.leaderboard["model_id"]
    check_ignore_cols_automl(models, names, x, y)

    print("AutoML with x and y as col indexes, train, valid, and test")
    aml.train(x=[2, 3, 4], y=1, training_frame=ds.train, validation_frame=ds.valid, leaderboard_frame=ds.test)
    print("AutoML leaderboard")
    print(aml.leaderboard)
    models = aml.leaderboard["model_id"]
    check_ignore_cols_automl(models, names, x, y)

    print("AutoML with x as a str list, y as a col index, train, valid, and test")
    aml.train(x=x, y=1, training_frame=ds.train, validation_frame=ds.valid, leaderboard_frame=ds.test)
    print("AutoML leaderboard")
    print(aml.leaderboard)
    models = aml.leaderboard["model_id"]
    check_ignore_cols_automl(models, names, x, y)

    print("AutoML with x as col indexes, y as a str, train, valid, and test")
    aml.train(x=[2,3,4], y=y, training_frame=ds.train, validation_frame=ds.valid, leaderboard_frame=ds.test)
    print("AutoML leaderboard")
    print(aml.leaderboard)
    models = aml.leaderboard["model_id"]
    check_ignore_cols_automl(models, names, x, y)


pu.run_tests([
    test_columns_not_in_x_and_y_are_ignored
])
