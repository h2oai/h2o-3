import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils



import random
import inspect

def cv_carsGBM():

    # read in the dataset and construct training set (and validation set)
    cars =  h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))

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
    # 1. check that cv metrics are the same over repeated "Modulo" runs
    nfolds = random.randint(3,10)
    gbm1 = h2o.gbm(y=cars[response_col], x=cars[predictors], nfolds=nfolds, distribution=distribution, ntrees=5,
                   fold_assignment="Modulo")
    gbm2 = h2o.gbm(y=cars[response_col], x=cars[predictors], nfolds=nfolds, distribution=distribution, ntrees=5,
                   fold_assignment="Modulo")
    pyunit_utils.check_models(gbm1, gbm2, True)

    # 2. check that cv metrics are different over repeated "Random" runs
    nfolds = random.randint(3,10)
    gbm1 = h2o.gbm(y=cars[response_col], x=cars[predictors], nfolds=nfolds, distribution=distribution, ntrees=5,
                   fold_assignment="Random")
    gbm2 = h2o.gbm(y=cars[response_col], x=cars[predictors], nfolds=nfolds, distribution=distribution, ntrees=5,
                   fold_assignment="Random")
    try:
        pyunit_utils.check_models(gbm1, gbm2, True)
        assert False, "Expected models to be different over repeated Random runs"
    except AssertionError:
        assert True

    # 3. folds_column
    num_folds = random.randint(2,5)
    fold_assignments = h2o.H2OFrame([[random.randint(0,num_folds-1) for f in range(cars.nrow)]])
    fold_assignments.set_names(["fold_assignments"])
    cars = cars.cbind(fold_assignments)
    gbm = h2o.gbm(y=cars[response_col], x=cars[predictors], training_frame=cars, distribution=distribution, ntrees=5,
                  fold_column="fold_assignments", keep_cross_validation_predictions=True)
    num_cv_models = len(gbm._model_json['output']['cross_validation_models'])
    assert num_cv_models==num_folds, "Expected {0} cross-validation models, but got " \
                                                    "{1}".format(num_folds, num_cv_models)
    cv_model1 = h2o.get_model(gbm._model_json['output']['cross_validation_models'][0]['name'])
    cv_model2 = h2o.get_model(gbm._model_json['output']['cross_validation_models'][1]['name'])

    # 4. keep_cross_validation_predictions
    cv_predictions = gbm1._model_json['output']['cross_validation_predictions']
    assert cv_predictions is None, "Expected cross-validation predictions to be None, but got {0}".format(cv_predictions)

    cv_predictions = gbm._model_json['output']['cross_validation_predictions']
    assert len(cv_predictions)==num_folds, "Expected the same number of cross-validation predictions " \
                                                          "as folds, but got {0}".format(len(cv_predictions))

    # # 5. manually construct models
    # fold1 = cars[cars["fold_assignments"]==0]
    # fold2 = cars[cars["fold_assignments"]==1]
    # manual_model1 = h2o.gbm(y=fold2[response_col],
    #                         x=fold2[predictors],
    #                         validation_y=fold1[response_col],
    #                         validation_x=fold1[predictors], ntrees=5,
    #                         distribution=distribution)
    # manual_model2 = h2o.gbm(y=fold1[response_col],
    #                         x=fold1[predictors],
    #                         validation_y=fold2[response_col],
    #                         validation_x=fold2[predictors], ntrees=5,
    #                         distribution=distribution)


    ## boundary cases
    # 1. nfolds = number of observations (leave-one-out cross-validation)
    gbm = h2o.gbm(y=cars[response_col], x=cars[predictors], nfolds=cars.nrow, distribution=distribution, ntrees=5,
                  fold_assignment="Modulo")

    # 2. nfolds = 0
    gbm1 = h2o.gbm(y=cars[response_col], x=cars[predictors], nfolds=0, distribution=distribution, ntrees=5)
    # check that this is equivalent to no nfolds
    gbm2 = h2o.gbm(y=cars[response_col], x=cars[predictors], distribution=distribution, ntrees=5)
    pyunit_utils.check_models(gbm1, gbm2)

    # 3. cross-validation and regular validation attempted
    gbm = h2o.gbm(y=cars[response_col], x=cars[predictors], nfolds=random.randint(3,10), validation_y=cars[response_col], ntrees=5,
                  validation_x=cars[predictors], distribution=distribution)


    ## error cases
    # 1. nfolds == 1 or < 0
    try:
        gbm = h2o.gbm(y=cars[response_col], x=cars[predictors], nfolds=random.sample([-1,1], 1)[0], ntrees=5,
                      distribution=distribution)
        assert False, "Expected model-build to fail when nfolds is 1 or < 0"
    except EnvironmentError:
        assert True

    # 2. more folds than observations
    try:
        gbm = h2o.gbm(y=cars[response_col], x=cars[predictors], nfolds=cars.nrow+1, distribution=distribution, ntrees=5,
                      fold_assignment="Modulo")
        assert False, "Expected model-build to fail when nfolds > nobs"
    except EnvironmentError:
        assert True

    # 3. fold_column and nfolds both specified
    try:
        gbm = h2o.gbm(y=cars[response_col], x=cars[predictors], nfolds=3, fold_column="fold_assignments", ntrees=5,
                      distribution=distribution, training_frame=cars)
        assert False, "Expected model-build to fail when fold_column and nfolds both specified"
    except EnvironmentError:
        assert True

    # 4. fold_column and fold_assignment both specified
    try:
        gbm = h2o.gbm(y=cars[response_col], x=cars[predictors], fold_assignment="Random", fold_column="fold_assignments", ntrees=5,
                      distribution=distribution, training_frame=cars)
        assert False, "Expected model-build to fail when fold_column and fold_assignment both specified"
    except EnvironmentError:
        assert True



if __name__ == "__main__":
    pyunit_utils.standalone_test(cv_carsGBM)
else:
    cv_carsGBM()
