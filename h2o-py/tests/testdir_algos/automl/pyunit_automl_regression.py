from __future__ import print_function
from __future__ import print_function
import sys, os

sys.path.insert(1, os.path.join("..","..","..",".."))
import h2o
from h2o.automl import H2OAutoML
from tests import pyunit_utils as pu

from _automl_utils import import_dataset


def test_default_automl_with_regression_task():
    ds = import_dataset('regression')
    aml = H2OAutoML(max_models=2,
                    project_name='aml_regression')

    aml.train(y=ds.target, training_frame=ds.train, validation_frame=ds.valid, leaderboard_frame=ds.test)
    print(aml.leader)
    print(aml.leaderboard)
    assert aml.leaderboard.columns == ["model_id", "mean_residual_deviance", "rmse", "mse", "mae", "rmsle"]


def test_workaround_for_distribution():
    try:
        h2o.rapids("(setproperty \"{}\" \"{}\")".format("sys.ai.h2o.automl.algo_parameters.all.enabled", "true"))
        ds = import_dataset('regression')
        aml = H2OAutoML(project_name="py_test",
                        algo_parameters=dict(
                            distribution='poisson',
                            family='poisson',
                        ),
                        exclude_algos=['StackedEnsemble'],
                        max_runtime_secs=60,
                        seed=1)
        aml.train(y=ds.target, training_frame=ds.train)
        model_names = [aml.leaderboard[i, 0] for i in range(0, (aml.leaderboard.nrows))]
        for mn in model_names:
            m = h2o.get_model(mn)
            dist = m.params['distribution'] if 'distribution' in m.params else m.params['family'] if 'family' in m.params else None
            print("{}: distribution = {}".format(mn, dist))
    except:
        h2o.rapids("(setproperty \"{}\" \"{}\")".format("sys.ai.h2o.automl.algo_parameters.all.enabled", "false"))


pu.run_tests([
    test_default_automl_with_regression_task,
    test_workaround_for_distribution,
])
