import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def pyunit_mean_per_class_error():
    gbm = H2OGradientBoostingEstimator(nfolds=3, fold_assignment="Random", seed=1234)

    ## Binomial
    cars = h2o.import_file("/users/arno/h2o-3/smalldata/junit/cars_20mpg.csv")
    cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
    r = cars[0].runif(seed=1234)
    train = cars[r > .2]
    valid = cars[r <= .2]
    response_col = "economy_20mpg"
    predictors = ["displacement","power","weight","acceleration","year"]
    gbm.distribution = "bernoulli"
    gbm.train(y=response_col, x=predictors, validation_frame=valid, training_frame=train)
    print(gbm)
    mpce = gbm.mean_per_class_error([0.5,0.8]) ## different thresholds
    assert (abs(mpce[0][1] - 0.004132231404958664) < 1e-5)
    assert (abs(mpce[1][1] - 0.021390374331550777) < 1e-5)



    ## Multinomial
    cars = h2o.import_file("/users/arno/h2o-3/smalldata/junit/cars_20mpg.csv")
    cars["cylinders"] = cars["cylinders"].asfactor()
    r = cars[0].runif(seed=1234)
    train = cars[r > .2]
    valid = cars[r <= .2]
    response_col = "cylinders"
    predictors = ["displacement","power","weight","acceleration","year"]
    gbm.distribution="multinomial"
    gbm.train(x=predictors,y=response_col, training_frame=train, validation_frame=valid)
    print(gbm)
    mpce = gbm.mean_per_class_error(train=True)
    assert( mpce == 0 )
    mpce = gbm.mean_per_class_error(valid=True)
    assert(abs(mpce - 0.207142857143 ) < 1e-5)
    mpce = gbm.mean_per_class_error(xval=True)
    assert(abs(mpce - 0.350071715433 ) < 1e-5)

if __name__ == "__main__":
    pyunit_utils.standalone_test(pyunit_mean_per_class_error)
else:
    pyunit_mean_per_class_error
