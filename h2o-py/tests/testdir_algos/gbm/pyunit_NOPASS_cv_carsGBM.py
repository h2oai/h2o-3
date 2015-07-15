import sys
sys.path.insert(1, "../../../")
import h2o
import random

def cv_carsGBM(ip,port):
    # Connect to h2o
    h2o.init(ip,port)

    # read in the dataset and construct training set (and validation set)
    cars =  h2o.import_frame(path=h2o.locate("smalldata/junit/cars_20mpg.csv"))

    # choose the type model-building exercise (multinomial classification or regression). 0:regression, 1:binomial,
    # 2:multinomial
    problem = random.sample(range(3),1)[0]

    # pick the predictors and response column, along with the correct distribution
    predictors = ["displacement","power","weight","acceleration","year"]
    if problem == 1   :
        response_col = "economy_20mpg"
        distribution = "bernoulli"
        cars[response_col] = cars[response_col].asfactor()
    elif problem == 2 :
        response_col = "cylinders"
        distribution = "multinomial"
        cars[response_col] = cars[response_col].asfactor()
    else              :
        response_col = "economy"
        distribution = "gaussian"

    print "Distribution: {0}".format(distribution)
    print "Response column: {0}".format(response_col)

    ## cross-validation
    ## check that cv metrics are the same over repeated runs
    nfolds = random.randint(3,10)
    gbm1 = h2o.gbm(y=cars[response_col], x=cars[predictors], nfolds=nfolds, distribution=distribution)
    gbm2 = h2o.gbm(y=cars[response_col], x=cars[predictors], nfolds=nfolds, distribution=distribution)
    h2o.check_models(gbm1, gbm2)

    ## boundary cases
    # 1. nfolds = number of observations (leave-one-out cross-validation)
    gbm = h2o.gbm(y=cars[response_col], x=cars[predictors], nfolds=cars.nrow(), distribution=distribution)
    # TODO: manually construct the cross-validation metrics and compare
    # TODO: PUBDEV-1697

    # 2. nfolds = 0
    gbm1 = h2o.gbm(y=cars[response_col], x=cars[predictors], nfolds=0, distribution=distribution)
    # check that this is equivalent to no nfolds
    gbm2 = h2o.gbm(y=cars[response_col], x=cars[predictors], distribution=distribution)
    h2o.check_models(gbm1, gbm2)

    # 3. more folds than observations equivalent to leave-one-out
    gbm3 = h2o.gbm(y=cars[response_col], x=cars[predictors], nfolds=cars.nrow()+1, distribution=distribution)
    h2o.check_models(gbm, gbm3)

    ## error cases
    # 1. nfolds == 1 or < 0
    # TODO: PUBDEV-1696
    try:
        gbm = h2o.gbm(y=cars[response_col], x=cars[predictors], nfolds=random.randint(-10000,-1),
                      distribution=distribution)
        gbm = h2o.gbm(y=cars[response_col], x=cars[predictors], nfolds=1, distribution=distribution)
        assert False, "Expected model-build to fail when nfolds is 1 or < 0"
    except EnvironmentError:
        assert True

    # 2. cross-validation and regular validation attempted
    r = cars[0].runif()
    train = cars[r > .2]
    valid = cars[r <= .2]
    try:
        gbm = h2o.gbm(y=train[response_col], x=train[predictors], nfolds=random.randint(3,10), validation_y=valid[1],
                      validation_x=valid[predictors], distribution=distribution)
        assert False, "Expected model-build to fail when both cross-validation and regular validation is attempted"
    except EnvironmentError:
        assert True

    # TODO: what should the model metrics look like? add cross-validation metric check to pyunit_metric_json_check.

if __name__ == "__main__":
    h2o.run_test(sys.argv, cv_carsGBM)