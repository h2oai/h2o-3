from concurrent.futures import ThreadPoolExecutor, wait
from functools import partial
from itertools import chain
import os
import sys

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils as pu
from h2o.automl import H2OAutoML


def flatten(it):
    return list(chain.from_iterable(it))


def import_dataset():
    df = h2o.import_file(path=pu.locate("smalldata/prostate/prostate.csv"))
    target = "CAPSULE"
    df[target] = df[target].asfactor()
    return pu.ns(train=df, target=target)


def test_several_automl_instances_can_be_run_in_parallel():
    # this is not a recommended behaviour, but this should work nonetheless.
    parallel = 5
    models_per_run = 3
    amls = [H2OAutoML(max_models=models_per_run, nfolds=0, seed=1) 
            for _ in range(parallel)]
    ds = import_dataset()
    with ThreadPoolExecutor(max_workers=parallel) as executor:
        futures = []
        for aml in amls:
            train = partial(aml.train, y=ds.target, training_frame=ds.train)
            futures.append(executor.submit(train))
        wait(futures)
    
    project_names = [aml.project_name for aml in amls]
    print(project_names)
    assert len(set(project_names)) == parallel
    leaderboards = [aml.leaderboard for aml in amls]
    models = flatten([[lb[i, 0] for i in range(lb.nrows)] for lb in leaderboards])
    print(models)
    assert len(set(models)) == parallel * models_per_run


pu.run_tests([
    test_several_automl_instances_can_be_run_in_parallel
])

