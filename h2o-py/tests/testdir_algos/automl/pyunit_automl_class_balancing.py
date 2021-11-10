from __future__ import print_function
import sys, os

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from h2o.automl import H2OAutoML
from tests import pyunit_utils as pu

pu.load_module("_automl_utils", os.path.join(os.path.dirname(__file__)))
from _automl_utils import import_dataset, get_partitioned_model_names

max_models = 2


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
    aml.train(y=ds.target, training_frame=ds.train)
    base_models = get_partitioned_model_names(aml.leaderboard).base
    amodel = h2o.get_model(base_models[0])
    assert amodel.params['balance_classes']['actual'] is True
    assert amodel.params['max_after_balance_size']['actual'] == 3.0
    assert amodel.params['class_sampling_factors']['actual'] == [0.2, 1.4]


pu.run_tests([
    test_balance_classes,
])
