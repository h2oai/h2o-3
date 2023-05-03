import os
import sys

sys.path.insert(1, os.path.join("..","..",".."))
from h2o.automl import H2OAutoML
from tests import pyunit_utils as pu

from _leaderboard_utils import all_algos, check_leaderboard
from _automl_utils import import_dataset, get_partitioned_model_names


automl_seed = 23


def test_warn_on_empty_leaderboard():
    ds = import_dataset()
    aml = H2OAutoML(project_name="test_empty_leaderboard",
                    include_algos=[],
                    seed=1234)
    aml.train(y=ds.target, training_frame=ds.train)
    assert aml.leaderboard.nrow == 0
    warnings = aml.event_log[aml.event_log['level'] == 'WARN','message']
    last_warning = warnings[warnings.nrow-1,:].flatten()
    assert "Empty leaderboard" in last_warning


def test_leaderboard_with_all_algos():
    print("Check leaderboard for all algorithms")
    ds = import_dataset('multiclass', split=False)
    aml = H2OAutoML(project_name="py_aml_lb_test_all_algos",
                    max_models=12,
                    nfolds=2,
                    stopping_rounds=1,
                    stopping_tolerance=0.5,
                    seed=automl_seed)
    aml.train(y=ds.target, training_frame=ds.train)

    check_leaderboard(aml, [], ["mean_per_class_error", "logloss", "rmse", "mse"], "mean_per_class_error")


def test_leaderboard_with_no_algos():
    print("Check leaderboard for excluding all algos (empty leaderboard)")
    ds = import_dataset('binary', split=False)
    exclude_algos = all_algos
    aml = H2OAutoML(project_name="py_aml_lb_test_no_algo",
                    exclude_algos=exclude_algos,
                    max_runtime_secs=10,
                    nfolds=2,
                    stopping_rounds=1,
                    stopping_tolerance=0.5,
                    seed=automl_seed)
    aml.train(y=ds.target, training_frame=ds.train)

    lb = aml.leaderboard
    assert lb.nrows == 0
    check_leaderboard(aml, exclude_algos, [], None, None)


pu.run_tests([
    test_warn_on_empty_leaderboard,
    test_leaderboard_with_all_algos,
    test_leaderboard_with_no_algos,
])
