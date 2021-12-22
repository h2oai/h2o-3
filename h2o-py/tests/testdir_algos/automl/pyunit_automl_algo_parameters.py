from __future__ import print_function
import sys, os
import re

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
import h2o.exceptions
from h2o.automl import H2OAutoML
from tests import pyunit_utils as pu

from _automl_utils import import_dataset, get_partitioned_model_names


max_models = 5


def test_algo_parameter_can_be_applied_only_to_a_specific_algo():
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_specific_algo_param",
                    algo_parameters=dict(
                        GBM__monotone_constraints=dict(AGE=1)
                    ),
                    max_models=6,
                    seed=1)
    aml.train(y=ds.target, training_frame=ds.train)
    model_names = get_partitioned_model_names(aml.leaderboard).all
    models_supporting_monotone_constraints = [n for n in model_names if re.match(r"GBM|XGBoost", n)]
    assert next((m for m in models_supporting_monotone_constraints if m.startswith('GBM')), None), "There should be at least one GBM model"
    for m in models_supporting_monotone_constraints:
        model = h2o.get_model(m)
        mc_value = next(v['actual'] for n, v in model.params.items() if n == 'monotone_constraints')
        if m.startswith('GBM'):
            assert isinstance(mc_value, list)
            age = next((v for v in mc_value if v['key'] == 'AGE'), None)
            assert age is not None
            assert age['value'] == 1.0
        else:
            assert mc_value is None


def test_cannot_set_unauthorized_algo_parameter():
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_unauthorized_algo_param",
                    algo_parameters=dict(
                        score_tree_interval=7
                    ),
                    max_models=6,
                    seed=1)
    try:
        aml.train(y=ds.target, training_frame=ds.train)
    except h2o.exceptions.H2OResponseError as e:
        assert "algo_parameters: score_tree_interval" in str(e)


pu.run_tests([
    test_algo_parameter_can_be_applied_only_to_a_specific_algo,
    test_cannot_set_unauthorized_algo_parameter,
])
