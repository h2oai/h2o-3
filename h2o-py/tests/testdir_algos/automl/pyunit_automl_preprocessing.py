from __future__ import print_function
import sys, os

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
    
    fr = df.split_frame(ratios=[.8,.1], seed=seed)
    return pu.ns(train=fr[0], valid=fr[1], test=fr[2], target=target)


def test_target_encoding():
    ds = import_dataset()
    aml = H2OAutoML(project_name="automl_with_te",
                    max_models=3,
                    preprocessing=['targetencoding'],
                    seed=1)
    aml.train(y=ds.target, training_frame=ds.train, leaderboard_frame=ds.test)
    lb = aml.leaderboard
    print(lb)
    # we can't really verify from client if TE was correctly applied... so just using a poor man's check:
    mem_keys = h2o.ls().key
    # print(mem_keys)
    assert any(k.startswith("TargetEncoding_AutoML") for k in mem_keys)


pu.run_tests([
    test_target_encoding,
])
