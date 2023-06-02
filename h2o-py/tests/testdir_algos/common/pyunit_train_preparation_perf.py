import sys
import time
sys.path.insert(1, "../../../")

import h2o
from h2o.estimators.estimator_base import H2OEstimator
from tests import pyunit_utils as pu


class DummyEstimator(H2OEstimator):
    
    def __init__(self):
        super(DummyEstimator, self).__init__()
        self._parms = {}

    def _train(self, parms, verbose=False):
        pass


def test_basic_estimator_preparation_perf_with_x():
    dummy = DummyEstimator()
    shape = (5, 100000)  # just need a very wide dataset as preparation is mainly working on columns selection
    data_start = time.time()
    names = ["Col_"+str(n) for n in range(shape[1])]
    y = names[len(names)//2]  # average worst scenario
    x = [n for i, n in enumerate(names) if i % 2]   # average worst scenario regardless what preparation is doing
    train_fr = h2o.H2OFrame({n: list(range(shape[0])) for n in names})
    data_duration = time.time() - data_start
    print("data preparation/upload took {}s".format(data_duration))
    training_start = time.time()
    dummy.train(x=x, y=y, training_frame=train_fr, validation_frame=train_fr)
    training_duration = time.time() - training_start
    print("training preparation took {}s".format(training_duration))
    assert training_duration < 10  # generous upper limit for slow Jenkins (obtaining around 1-2s on macOS) 


def test_basic_estimator_preparation_perf_with_ignored_columns():
    dummy = DummyEstimator()
    shape = (5, 100000) 
    data_start = time.time()
    names = ["Col_"+str(n) for n in range(shape[1])]
    y = names[len(names)//2]  # average worst scenario
    ignored = [n for i, n in enumerate(names) if i % 2]   # average worst scenario regardless what preparation is doing
    train_fr = h2o.H2OFrame({n: list(range(shape[0])) for n in names})
    data_duration = time.time() - data_start
    print("data preparation/upload took {}s".format(data_duration))
    training_start = time.time()
    dummy.train(y=y, training_frame=train_fr, validation_frame=train_fr, ignored_columns=ignored)
    training_duration = time.time() - training_start
    print("training preparation took {}s".format(training_duration))
    assert training_duration < 10  # generous upper limit for slow Jenkins (obtaining around 1-2s on macOS) 



pu.run_tests([
    test_basic_estimator_preparation_perf_with_x,
    test_basic_estimator_preparation_perf_with_ignored_columns,
])

