import sys
sys.path.insert(1, "../../../")
import h2o
import random

def cv_carsGBM(ip,port):

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
    ## check that cv metrics are the same over repeated "Modulo" runs
    nfolds = random.randint(3,10)
    gbm1 = h2o.gbm(y=cars[response_col], x=cars[predictors], nfolds=nfolds, distribution=distribution,
                   fold_assignment="Modulo")
    gbm2 = h2o.gbm(y=cars[response_col], x=cars[predictors], nfolds=nfolds, distribution=distribution,
                   fold_assignment="Modulo")
    h2o.check_models(gbm1, gbm2, True)

    ## check that cv metrics are different over repeated "Random" runs
    nfolds = random.randint(3,10)
    gbm1 = h2o.gbm(y=cars[response_col], x=cars[predictors], nfolds=nfolds, distribution=distribution,
                   fold_assignment="Random")
    gbm2 = h2o.gbm(y=cars[response_col], x=cars[predictors], nfolds=nfolds, distribution=distribution,
                   fold_assignment="Random")
    try:
        h2o.check_models(gbm1, gbm2, True)
        assert False, "Expected models to be different over repeated Random runs"
    except AssertionError:
        assert True


    ## boundary cases
    # 1. nfolds = number of observations (leave-one-out cross-validation)
    # TODO: manually construct the cross-validation metrics and compare
    gbm = h2o.gbm(y=cars[response_col], x=cars[predictors], nfolds=cars.nrow(), distribution=distribution,
                  fold_assignment="Modulo")

    # 2. nfolds = 0
    gbm1 = h2o.gbm(y=cars[response_col], x=cars[predictors], nfolds=0, distribution=distribution)
    # check that this is equivalent to no nfolds
    gbm2 = h2o.gbm(y=cars[response_col], x=cars[predictors], distribution=distribution)
    h2o.check_models(gbm1, gbm2)

    # 3. cross-validation and regular validation attempted
    gbm = h2o.gbm(y=cars[response_col], x=cars[predictors], nfolds=random.randint(3,10), validation_y=cars[response_col],
                  validation_x=cars[predictors], distribution=distribution)


    ## error cases
    # 1. nfolds == 1 or < 0
    try:
        gbm = h2o.gbm(y=cars[response_col], x=cars[predictors], nfolds=random.sample([-1,1], 1)[0],
                      distribution=distribution)
        assert False, "Expected model-build to fail when nfolds is 1 or < 0"
    except EnvironmentError:
        assert True

    # 2. more folds than observations
    try:
        gbm = h2o.gbm(y=cars[response_col], x=cars[predictors], nfolds=cars.nrow()+1, distribution=distribution,
                      fold_assignment="Modulo")
        assert False, "Expected model-build to fail when nfolds > nobs"
    except EnvironmentError:
        assert True

    # TODO: what should the model metrics look like? add cross-validation metric check to pyunit_metric_json_check.

if __name__ == "__main__":
    h2o.run_test(sys.argv, cv_carsGBM)