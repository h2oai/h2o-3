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


def test_basic_estimator_preparation_is_fast_enough():
    dummy = DummyEstimator()
    # for some mysterious reason, the parser fails if uploading more than 331186 columns: magic number?
    # using this upper limit for now, but this is fishy:
    #
    # ~/repos/h2o/h2o-3/h2o-py/h2o/h2o.py in parse_setup(raw_frames, destination_frame, header, separator, column_names, column_types, na_strings, skipped_columns, custom_non_data_line_markers, partition_by, quotechar, escapechar)
    #     874             if len(column_names) != len(j["column_types"]): raise ValueError(
    #     875                 "length of col_names should be equal to the number of columns: %d vs %d"
    # --> 876                 % (len(column_names), len(j["column_types"])))
    #     877         j["column_names"] = column_names
    #     878         counter = 0
    # 
    # ValueError: length of col_names should be equal to the number of columns: 1000000 vs 331186
    shape = (5, 331186)  # just need a very wide dataset as preparation is mainly working on columns selection
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


pu.run_tests([
    test_basic_estimator_preparation_is_fast_enough,
])

