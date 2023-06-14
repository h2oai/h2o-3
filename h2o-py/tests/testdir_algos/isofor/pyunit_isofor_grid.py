import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import random
from h2o.estimators.isolation_forest import H2OIsolationForestEstimator
from h2o.grid.grid_search import H2OGridSearch
from h2o.frame import H2OFrame
import numpy as np
import pandas as pd


def grid_synthetic_IF():
    N = 1000
    cont = 0.05

    regular_data = np.random.normal(0, 0.5, (int(N * (1 - cont)), 2))
    anomaly_data = np.column_stack((np.random.normal(-1.5, 1, int(N * cont)), np.random.normal(1.5, 1, int(N * cont))))

    regular_pd = pd.DataFrame(
        {'x': regular_data[:, 0], 'y': regular_data[:, 1], 'label': np.zeros(regular_data.shape[0])})
    anomaly_pd = pd.DataFrame(
        {'x': anomaly_data[:, 0], 'y': anomaly_data[:, 1], 'label': np.ones(anomaly_data.shape[0])})

    dataset = H2OFrame(regular_pd.append(anomaly_pd).sample(frac=1))

    train_with_label, test = dataset.split_frame([0.8])
    train = train_with_label.drop(["label"])
    test["label"] = test["label"].asfactor()

    grid_space = {
        'max_depth': random.sample(list(range(1, 6)), random.randint(2, 3))
    }
    print("Grid space: {0}".format(grid_space))

    predictors = ["x", "y"]

    print("Constructing the grid of IF models...")
    if_grid = H2OGridSearch(H2OIsolationForestEstimator, hyper_params=grid_space)
    if_grid.train(x=predictors, training_frame=train,
                  validation_frame=test, validation_response_column="label")

    print("Check correct type value....")
    model_type = if_grid[0].type
    assert model_type == 'unsupervised', "Type of model ({0}) is incorrect, expected value is 'unsupervised'.".format(model_type)

    print("Performing various checks of the constructed grid...")

    print("Check cardinality of grid, that is, the correct number of models have been created...")
    size_of_grid_space = 1
    for v in list(grid_space.values()):
        size_of_grid_space = size_of_grid_space * len(v)
    actual_size = len(if_grid)
    print("Expected size of grid space: {0}".format(size_of_grid_space))
    assert size_of_grid_space == actual_size, "Expected size of grid to be {0}, but got {1}" \
                                              "".format(size_of_grid_space, actual_size)
    print(if_grid)


if __name__ == "__main__":
    pyunit_utils.standalone_test(grid_synthetic_IF)
else:
    grid_synthetic_IF()
