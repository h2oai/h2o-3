from __future__ import print_function
import sys, os

sys.path.insert(1, os.path.join("..","..",".."))
from h2o.automl import H2OAutoML
from tests import pyunit_utils as pu

pu.load_module("_automl_utils", os.path.join(os.path.dirname(__file__)))
from _automl_utils import import_dataset

max_models = 2


def test_predict_on_train_set():
    print("Check predict, leader, and leaderboard")
    print("AutoML run with x not provided and train set only")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml6", 
                    stopping_rounds=3, 
                    stopping_tolerance=0.001, 
                    stopping_metric="AUC", 
                    max_models=max_models, 
                    seed=1234)
    aml.train(y=ds.target, training_frame=ds.train)
    print("Check leaderboard")
    print(aml.leaderboard)
    print("Check predictions")
    print(aml.predict(ds.train))
    

pu.run_tests([
    test_predict_on_train_set,
])
