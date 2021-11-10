from __future__ import print_function
import sys, os
import re

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
import h2o.exceptions
from h2o.automl import H2OAutoML
from tests import pyunit_utils as pu

pu.load_module("_automl_utils", os.path.join(os.path.dirname(__file__)))
from _automl_utils import import_dataset, get_partitioned_model_names

max_models = 5


def test_monotone_constraints():
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_monotone_constraints",
                    monotone_constraints=dict(AGE=1, VOL=-1),  # constraints just for the sake of testing
                    max_models=6,
                    seed=1)
    aml.train(y=ds.target, training_frame=ds.train)
    model_names = get_partitioned_model_names(aml.leaderboard).all
    models_supporting_monotone_constraints = [n for n in model_names if re.match(r"GBM|XGBoost", n)]
    assert len(models_supporting_monotone_constraints) < len(model_names), \
        "models not supporting the constraint should not have been skipped"
    for m in models_supporting_monotone_constraints:
        model = h2o.get_model(m)
        value = next(v['actual'] for n, v in model.params.items() if n == 'monotone_constraints')
        assert isinstance(value, list)
        assert len(value) == 2
        age = next((v for v in value if v['key'] == 'AGE'), None)
        assert age is not None
        assert age['value'] == 1.0
        vol = next((v for v in value if v['key'] == 'VOL'), None)
        assert vol is not None
        assert vol['value'] == -1.0


def test_monotone_constraints_can_be_passed_as_algo_parameter():
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_monotone_constraints",
                    algo_parameters=dict(
                        monotone_constraints=dict(AGE=1, VOL=-1),  # constraints just for the sake of testing
                        # ntrees=10,
                    ),
                    max_models=6,
                    seed=1)
    aml.train(y=ds.target, training_frame=ds.train)
    model_names = get_partitioned_model_names(aml.leaderboard).all
    models_supporting_monotone_constraints = [n for n in model_names if re.match(r"GBM|XGBoost", n)]
    assert len(models_supporting_monotone_constraints) < len(model_names), \
        "models not supporting the constraint should not have been skipped"
    for m in models_supporting_monotone_constraints:
        model = h2o.get_model(m)
        value = next(v['actual'] for n, v in model.params.items() if n == 'monotone_constraints')
        # print(param)
        assert isinstance(value, list)
        assert len(value) == 2
        age = next((v for v in value if v['key'] == 'AGE'), None)
        assert age is not None
        assert age['value'] == 1.0
        vol = next((v for v in value if v['key'] == 'VOL'), None)
        assert vol is not None
        assert vol['value'] == -1.0

    # models_supporting_ntrees = [n for n in model_names if re.match(r"DRF|GBM|XGBoost|XRT", n)]
    # assert len(models_supporting_ntrees) > 0
    # for m in models_supporting_ntrees:
    #     model = h2o.get_model(m)
    #     value = next(v['actual'] for n, v in model.params.items() if n == 'ntrees')
    #     assert value == 10


pu.run_tests([
    test_monotone_constraints,
    test_monotone_constraints_can_be_passed_as_algo_parameter,
])
