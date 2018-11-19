from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
import random
import copy
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from h2o.grid.grid_search import H2OGridSearch

def grid_cars_GLM():

    cars =  h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    r = cars[0].runif(seed=42)
    train = cars[r > .2]

    validation_scheme = random.randint(1,3) # 1:none, 2:cross-validation, 3:validation set
    print("Validation scheme: {0}".format(validation_scheme))
    if validation_scheme == 2:
        nfolds = 2
        print("Nfolds: 2")
    if validation_scheme == 3:
        valid = cars[r <= .2]

    grid_space = pyunit_utils.make_random_grid_space(algo="glm")
    print("Grid space: {0}".format(grid_space))

    predictors = ["displacement","power","weight","acceleration","year"]
    if grid_space['family'][0] == 'binomial':
        response_col = "economy_20mpg"
        true_model_type = "classifier"
    elif grid_space['family'][0] == 'gaussian':
        response_col = "economy"
        true_model_type = "regressor"
    else:
        response_col = "cylinders"
        true_model_type = "regressor"

    print("Predictors: {0}".format(predictors))
    print("Response: {0}".format(response_col))

    if grid_space['family'][0] in ['binomial', 'multinomial']:
        print("Converting the response column to a factor...")
        train[response_col] = train[response_col].asfactor()
        if validation_scheme == 3:
            valid[response_col] = valid[response_col].asfactor()

    #grid_space.update({"lambda":[0.1,0.05,0.01]})
    family = grid_space.pop('family')[0]
    print("Grid space: {0}".format(grid_space))
    print("Constructing the grid of glm models...")
    print("family = ",family)
    cars_glm_grid = H2OGridSearch(H2OGeneralizedLinearEstimator, hyper_params=grid_space)
    if validation_scheme == 1:
        cars_glm_grid.train(x=predictors,y=response_col,training_frame=train, family=family)
    elif validation_scheme == 2:
        cars_glm_grid.train(x=predictors,y=response_col,training_frame=train,nfolds=nfolds, family=family)
    else:
        cars_glm_grid.train(x=predictors,y=response_col,training_frame=train,validation_frame=valid, family=family)

    for model in cars_glm_grid:
      assert isinstance(model, H2OGeneralizedLinearEstimator)

    print("Performing various checks of the constructed grid...")

    print("Check cardinality of grid, that is, the correct number of models have been created...")
    size_of_grid_space = 1
    for v in list(grid_space.values()):
        size_of_grid_space = size_of_grid_space * len(v)
    actual_size = len(cars_glm_grid)
    assert size_of_grid_space ==  actual_size, "Expected size of grid to be {0}, but got {1}" \
                                               "".format(size_of_grid_space,actual_size)

    print("Check correct type value....")
    model_type = cars_glm_grid[0].type
    assert model_type == true_model_type, "Type of model ({0}) is incorrect, expected value is {1}.".format(model_type, true_model_type)

    print("Duplicate-entries-in-grid-space check")
    new_grid_space = copy.deepcopy(grid_space)
    for name in list(grid_space.keys()):
        if not name == "family":
            new_grid_space[name] = grid_space[name] + grid_space[name]
    print("The new search space: {0}".format(new_grid_space))
    print("Constructing the new grid of glm models...")
    cars_glm_grid2 = H2OGridSearch(H2OGeneralizedLinearEstimator, hyper_params=new_grid_space)
    if validation_scheme == 1:
        cars_glm_grid2.train(x=predictors,y=response_col,training_frame=train, family=family)
    elif validation_scheme == 2:
        cars_glm_grid2.train(x=predictors,y=response_col,training_frame=train,nfolds=nfolds, family=family)
    else:
        cars_glm_grid2.train(x=predictors,y=response_col,training_frame=train,validation_frame=valid, family=family)
    actual_size2 = len(cars_glm_grid2)
    assert actual_size == actual_size2, "Expected duplicates to be ignored. Without dups grid size: {0}. With dups " \
                                        "size: {1}".format(actual_size, actual_size2)

    print("Check that the hyper_params that were passed to grid, were used to construct the models...")
    for name in list(grid_space.keys()):
        print(name)
        pyunit_utils.expect_model_param(cars_glm_grid, name, grid_space[name])

if __name__ == "__main__":
    pyunit_utils.standalone_test(grid_cars_GLM)
else:
    grid_cars_GLM()
