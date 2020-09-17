from __future__ import print_function
import sys, os
import re

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
import h2o.exceptions
from tests import pyunit_utils as pu
from h2o.automl import H2OAutoML


def import_dataset(seed=0, mode='binary'):
    df = h2o.import_file(path=pu.locate("smalldata/titanic/titanic_expanded.csv"), header=1)
    target = dict(
        binary='survived',
        multiclass='pclass',
        regression='fare'
    )[mode]
    
    # df = h2o.import_file(path=pu.locate("smalldata/prostate/prostate.csv"))
    # target = "CAPSULE"
    # df[target] = df[target].asfactor()
    # for col in ["RACE", "DPROS", "DCAPS", "GLEASON"]:
    #     df[col] = df[col].asfactor()
    
    fr = df.split_frame(ratios=[.8,.1], seed=seed)
    return pu.ns(train=fr[0], valid=fr[1], test=fr[2], target=target)


def test_target_encoding_with_nfolds_cv():
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_target_encoding",
                    max_models=10,
                    preprocessing=['targetencoding'],
                    seed=1)
    aml.train(y=ds.target, training_frame=ds.train, leaderboard_frame=ds.test)
    print(aml.leaderboard)


pu.run_tests([
    test_target_encoding_with_nfolds_cv,
])
