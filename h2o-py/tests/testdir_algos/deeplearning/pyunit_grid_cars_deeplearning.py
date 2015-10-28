import sys
import os
import copy
import random
sys.path.insert(1, os.path.join("..",".."))
import h2o
from tests import pyunit_utils
from h2o.grid.grid_search import H2OGridSearch

def deeplearning_grid_cars():

    from h2o.estimators.deeplearning import H2ODeepLearningEstimator

    cars = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    r = cars[0].runif(seed=42)
    train = cars[r > .2]

    validation_scheme = random.randint(1,3) # 1:none, 2:cross-validation, 3:validation set
    print "Validation scheme: {0}".format(validation_scheme)
    if validation_scheme == 2:
        nfolds = 2
        print "Nfolds: 2"
    if validation_scheme == 3:
        valid = cars[r <= .2]

    grid_space = pyunit_utils.make_random_grid_space(algo="dl")
    print "Grid space: {0}".format(grid_space)

    predictors = ["displacement","power","weight","acceleration","year"]
    if grid_space['distribution'][0] == 'bernoulli':
        response_col = "economy_20mpg"
    elif grid_space['distribution'][0] == 'gaussian':
        response_col = "economy"
    else:
        response_col = "cylinders"

    print "Predictors: {0}".format(predictors)
    print "Response: {0}".format(response_col)

    if grid_space['distribution'][0] in ['bernoulli', 'multinomial']:
        print "Converting the response column to a factor..."
        train[response_col] = train[response_col].asfactor()
        if validation_scheme == 3:
            valid[response_col] = valid[response_col].asfactor()

    print "Constructing the grid of gbm models..."
    cars_dl_grid = H2OGridSearch(H2ODeepLearningEstimator, hyper_params=grid_space)
    if validation_scheme == 1:
        cars_dl_grid.train(x=predictors,y=response_col,training_frame=train)
    elif validation_scheme == 2:
        cars_dl_grid.train(x=predictors,y=response_col,training_frame=train,nfolds=nfolds)
    else:
        cars_dl_grid.train(x=predictors,y=response_col,training_frame=train,validation_frame=valid)

    print "Performing various checks of the constructed grid..."

    print "Check cardinality of grid, that is, the correct number of models have been created..."
    size_of_grid_space = 1
    for v in grid_space.values():
        size_of_grid_space = size_of_grid_space * len(v)
    actual_size = len(cars_dl_grid)
    assert size_of_grid_space ==  actual_size, "Expected size of grid to be {0}, but got {1}" \
                                               "".format(size_of_grid_space,actual_size)
    print "Duplicate-entries-in-grid-space check"
    new_grid_space = copy.deepcopy(grid_space)
    for name in grid_space.keys():
        if not name == "distribution":
            new_grid_space[name] = grid_space[name] + grid_space[name]
    print "The new search space: {0}".format(new_grid_space)
    print "Constructing the new grid of gbm models..."
    cars_dl_grid2 = H2OGridSearch(H2ODeepLearningEstimator, hyper_params=new_grid_space)
    if validation_scheme == 1:
        cars_dl_grid2.train(x=predictors,y=response_col,training_frame=train)
    elif validation_scheme == 2:
        cars_dl_grid2.train(x=predictors,y=response_col,training_frame=train,nfolds=nfolds)
    else:
        cars_dl_grid2.train(x=predictors,y=response_col,training_frame=train,validation_frame=valid)
    actual_size2 = len(cars_dl_grid2)
    assert actual_size == actual_size2, "Expected duplicates to be ignored. Without dups grid size: {0}. With dups " \
                                    "size: {1}".format(actual_size, actual_size2)

    print "Check that the hyper_params that were passed to grid, were used to construct the models..."
    for name in grid_space.keys():
        pyunit_utils.expect_model_param(cars_dl_grid, name, grid_space[name])



if __name__ == "__main__":
    pyunit_utils.standalone_test(deeplearning_grid_cars)
else:
    deeplearning_grid_cars()