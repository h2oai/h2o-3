from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..","..",".."))
import h2o
from tests import pyunit_utils as pu
from h2o.automl import H2OAutoML


def import_dataset():
    df = h2o.import_file(path=pu.locate("smalldata/extdata/australia.csv"))
    fr = df.split_frame(ratios=[.8,.1])
    target = "runoffnew"
    return pu.ns(target=target, train=fr[0], valid=fr[1], test=fr[2])
    
    
def australia_automl():
    ds = import_dataset()
    aml = H2OAutoML(max_models=2, stopping_rounds=3, stopping_tolerance=0.001)

    print("AutoML (Regression) run with x not provided with train, valid, and test")
    aml.train(y=ds.target, training_frame=ds.train,validation_frame=ds.valid, leaderboard_frame=ds.test)
    print(aml.leader)
    print(aml.leaderboard)
    assert set(aml.leaderboard.columns) == set(["model_id", "mean_residual_deviance","rmse", "mse", "mae", "rmsle"])


def test_workaround_for_distribution():
    try:
        h2o.rapids("(setproperty \"{}\" \"{}\")".format("sys.ai.h2o.automl.algo_parameters.all.enabled", "true"))
        ds = import_dataset()
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
        h2o.rapids("(setproperty \"{}\" \"{}\")".format("sys.ai.h2o.algos.evaluate_auto_model_parameters", "false"))


pu.run_tests([
    australia_automl,
    test_workaround_for_distribution,
])
