from builtins import range
import sys, os, time
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def test_if_h_fast_enough():
    import numpy as np
    import pandas as pd
    DATA_COUNT = 10000
    RANDOM_SEED = 137
    TRAIN_FRACTION = 0.9
    
    np.random.seed(RANDOM_SEED)
    
    xs = pd.DataFrame(np.random.uniform(size = (DATA_COUNT, 3)))
    xs.columns = ['x0', 'x1', 'x2']
    
    y = pd.DataFrame(xs.x0*xs.x1 + xs.x2 + pd.Series(0.1*np.random.randn(DATA_COUNT)))
    y.columns = ['y']
    
    train_ilocs = range(int(TRAIN_FRACTION*DATA_COUNT))
    test_ilocs = range(int(TRAIN_FRACTION*DATA_COUNT), DATA_COUNT)
    
    merged_data = pd.concat((xs, y), axis=1)

    gbm = H2OGradientBoostingEstimator(max_depth=50)
    
    data_sample = h2o.H2OFrame(merged_data)
    gbm.train(x=['x0', 'x1', 'x2'], y='y', training_frame=data_sample)
    
    
    h2o_single_pair_start_time = time.time()
    h2o_single_pair_h = gbm.h(frame=data_sample, variables=['x0', 'x1'])
    h2o_single_pair_end_time = time.time()
    print("Result: {}; Diff: {}".format(h2o_single_pair_h, h2o_single_pair_h - 0.3804505590934481))
    print("Computing H took {}s".format(h2o_single_pair_end_time - h2o_single_pair_start_time))

    assert abs(h2o_single_pair_h - 0.3804505590934481) < 1e-10  # Check the result with the pre-speedup result
    assert h2o_single_pair_end_time - h2o_single_pair_start_time < 90  # old version takes around 1764 s; locally it runs in less than 10s


    
if __name__ == "__main__":
  pyunit_utils.standalone_test(test_if_h_fast_enough)
else:
  test_if_h_fast_enough()
