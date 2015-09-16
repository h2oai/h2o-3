import sys
sys.path.insert(1, "../../")
import h2o, tests

def pubdev_random_cv():

    cars =  h2o.import_file(path=h2o.locate("smalldata/junit/cars_20mpg.csv"))
    response_col = "economy"
    distribution = "gaussian"
    predictors = ["displacement","power","weight","acceleration","year"]

    gbm1 = h2o.gbm(y=cars[response_col], x=cars[predictors], nfolds=3, distribution=distribution,
                   fold_assignment="Random")
    gbm2 = h2o.gbm(y=cars[response_col], x=cars[predictors], nfolds=3, distribution=distribution,
                   fold_assignment="Random")

    mse1 = gbm1.mse(xval=True)
    mse2 = gbm2.mse(xval=True)
    assert mse1 != mse2, "The first model has an MSE of {0} and the second model has an MSE of {1}. Expected the " \
                         "first to be different from the second.".format(mse1, mse2)

if __name__ == "__main__":
    tests.run_test(sys.argv, pubdev_random_cv)
