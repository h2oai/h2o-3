from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
import copy
from h2o.estimators.uplift_random_forest import H2OUpliftRandomForestEstimator
from h2o.grid.grid_search import H2OGridSearch


def grid_uplift_drf():

    data = h2o.import_file(path=pyunit_utils.locate("smalldata/uplift/upliftml_train.csv"))
    r = data[0].runif(seed=42)
    train = data[r > .2]
    valid = data[r <= .2]

    grid_space = pyunit_utils.make_random_grid_space(algo="uplift")
    print("Grid space: {0}".format(grid_space))

    predictors = ["feature_"+str(x) for x in range(1,13)]
    response_col = "outcome"
    treatment_col = "treatment"
    true_model_type = "binomial_uplift"

    print("Predictors: {0}".format(predictors))
    print("Response: {0}".format(response_col))

    train[response_col] = train[response_col].asfactor()
    valid[response_col] = valid[response_col].asfactor()
    train[treatment_col] = train[treatment_col].asfactor()
    valid[treatment_col] = valid[treatment_col].asfactor()

    print("Constructing the grid of uplift drf models...")
    uplift_grid = H2OGridSearch(H2OUpliftRandomForestEstimator, hyper_params=grid_space)
    uplift_grid.train(x=predictors, y=response_col, treatment_column=treatment_col, training_frame=train, 
                      validation_frame=valid)

    print("Check correct type value....")
    model_type = uplift_grid[0].type
    assert model_type == true_model_type, "Type of model ({0}) is incorrect, expected value is {1}."\
        .format(model_type, true_model_type)

    print("Performing various checks of the constructed grid...")

    print("Check cardinality of grid, that is, the correct number of models have been created...")
    size_of_grid_space = 1
    for v in list(grid_space.values()):
        size_of_grid_space = size_of_grid_space * len(v)
    actual_size = len(uplift_grid)
    assert size_of_grid_space == actual_size, "Expected size of grid to be {0}, but got {1}".format(
        size_of_grid_space, actual_size)

    print("Duplicate-entries-in-grid-space check")
    new_grid_space = copy.deepcopy(grid_space)
    for name in list(grid_space.keys()):
        if not name == "distribution":
            new_grid_space[name] = grid_space[name] + grid_space[name]
    print("The new search space: {0}".format(new_grid_space))
    print("Constructing the new grid of gbm models...")
    uplift_grid2 = H2OGridSearch(H2OUpliftRandomForestEstimator, hyper_params=new_grid_space)
    uplift_grid2.train(x=predictors, y=response_col, treatment_column=treatment_col, training_frame=train, 
                       validation_frame=valid)
    actual_size2 = len(uplift_grid2)
    assert actual_size == actual_size2, "Expected duplicates to be ignored. Without dups grid size: {0}. With dups " \
                                        "size: {1}".format(actual_size, actual_size2)

    print("Check that the hyper_params that were passed to grid, were used to construct the models...")
    for name in list(grid_space.keys()):
        pyunit_utils.expect_model_param(uplift_grid, name, grid_space[name])


if __name__ == "__main__":
    pyunit_utils.standalone_test(grid_uplift_drf)
else:
    grid_uplift_drf()
