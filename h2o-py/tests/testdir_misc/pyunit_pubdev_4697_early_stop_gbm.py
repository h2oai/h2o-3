import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils

import random
from h2o.estimators.gbm import H2OGradientBoostingEstimator
import numpy as np
import pandas as pd
from sklearn import datasets

NUM_SAMPLES = 1000
NUM_TREES = 5000

# A user has noticed that our early stopping failed for special datasets.  Turns out that there is a bug.
# For decreasing metrics, when the lastInK drop to zero, this implies that no more improvement is possible and
# the early stopping should return true instead of false.  Good catch and thank you, Craig Milhiser!
def generate_baseline_data(include_cat):
    X, y = datasets.make_friedman1(n_samples=NUM_SAMPLES, n_features=5, noise=100, random_state=1)

    # convert  to a binomial
    prob = 1 / (1 + np.exp(-y))
    y = np.random.binomial(1, prob)

    print('Event rate = {0:4.4f}'.format(np.sum(y) / NUM_SAMPLES))

    data = np.hstack((y.reshape(-1, 1), X))
    data = pd.DataFrame(data, columns=['y', 'x0', 'x1', 'x2', 'x3', 'x4'])

    if include_cat is True:
        data['c'] = data.apply(lambda row: 'A' if row.y == 1 else 'B', axis=1)

    return data

def test_early_stop_gbm():
    random.seed(1)
    np.random.seed(1)

    data = generate_baseline_data(include_cat=True)
    data_hex = h2o.H2OFrame(data,
                        destination_frame='data_cat',
                        column_types=['enum', 'real', 'real', 'real',
                                      'real', 'real', 'enum'])

    frames = data_hex.split_frame([0.8], ['train_cat', 'validate_cat'], seed=1)

    train_it(frames, ['x0', 'x1', 'x2', 'x3', 'x4', 'c'])

def train_it(frames, x):
    estimator = H2OGradientBoostingEstimator(distribution='bernoulli',
                                             ntrees=NUM_TREES,
                                             learn_rate=0.1,
                                             nfolds=0,
                                             score_tree_interval=20,
                                             stopping_rounds=3,
                                             stopping_tolerance=0.001,
                                             seed=1)
    estimator.train(x=x,
                    y='y',
                    training_frame=frames[0],
                    validation_frame=frames[1])


    num_trees_trained = (int(estimator.summary()
                             .as_data_frame()['number_of_trees']
                             .to_numpy()[0]))
    print('num trees trained = {0}'.format(num_trees_trained))
    assert num_trees_trained < NUM_TREES, "Early stopping is not work."


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_early_stop_gbm)
else:
    test_early_stop_gbm()
