import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.grid import H2OGridSearch



def grid_metric_accessors():

    cars = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    r = cars[0].runif()
    train = cars[r > .2]
    valid = cars[r <= .2]

    # regression
    response_col = "economy"
    distribution = "gaussian"
    predictors = ["displacement","power","weight","acceleration","year"]

    gbm = H2OGradientBoostingEstimator(nfolds=3,
                                       distribution=distribution,
                                       fold_assignment="Random")
    gbm_grid = H2OGridSearch(gbm, hyper_params=dict(ntrees=[1, 2, 3]))
    gbm_grid.train(x=predictors, y=response_col, training_frame=train, validation_frame=valid)

    # using list from http://docs.h2o.ai/h2o/latest-stable/h2o-docs/performance-and-prediction.html#regression
    for metric in ['r2', 'mse', 'rmse', 'rmsle', 'mae']:
        val = getattr(gbm_grid, metric)()
        assert isinstance(val, dict)
        for v in val.values():
            assert isinstance(v, float), "expected a float for metric {} but got {}".format(metric, v)


    # binomial
    cars = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
    r = cars[0].runif()
    train = cars[r > .2]
    valid = cars[r <= .2]
    response_col = "economy_20mpg"
    distribution = "bernoulli"
    predictors = ["displacement","power","weight","acceleration","year"]
    gbm = H2OGradientBoostingEstimator(nfolds=3, distribution=distribution, fold_assignment="Random")
    gbm_grid = H2OGridSearch(gbm, hyper_params=dict(ntrees=[1, 2, 3]))
    gbm_grid.train(x=predictors, y=response_col, training_frame=train, validation_frame=valid)

    # using list from http://docs.h2o.ai/h2o/latest-stable/h2o-docs/performance-and-prediction.html#classification
    # + common ones
    for metric in ['gini', 'logloss', 'auc', 'aucpr', 'mse', 'rmse']:
        val = getattr(gbm_grid, metric)()
        assert isinstance(val, dict)
        for v in val.values():
            assert isinstance(v, float), "expected a float for metric {} but got {}".format(metric, v)

    for metric in ['mcc', 'F1', 'F0point5', 'F2', 'accuracy', 'mean_per_class_error']:
        val = getattr(gbm_grid, metric)()
        assert isinstance(val, dict)
        for v in val.values():
            assert isinstance(v[0][1], float), "expected a float for metric {} but got {}".format(metric, v)



    # multinomial
    cars = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    cars["cylinders"] = cars["cylinders"].asfactor()
    r = cars[0].runif()
    train = cars[r > .2]
    valid = cars[r <= .2]
    response_col = "cylinders"
    distribution = "multinomial"
    predictors = ["displacement","power","weight","acceleration","year"]
    gbm = H2OGradientBoostingEstimator(nfolds=3, distribution=distribution, fold_assignment="Random", auc_type="MACRO_OVR")
    gbm_grid = H2OGridSearch(gbm, hyper_params=dict(ntrees=[1, 2, 3]))
    gbm_grid.train(x=predictors, y=response_col, training_frame=train, validation_frame=valid)

    # using list from http://docs.h2o.ai/h2o/latest-stable/h2o-docs/performance-and-prediction.html#classification
    # + common ones
    for metric in ['logloss', 'mse', 'rmse', 'mean_per_class_error', 'auc', 'aucpr']:
        val = getattr(gbm_grid, metric)()
        assert isinstance(val, dict)
        for v in val.values():
            assert isinstance(v, float), "expected a float for metric {} but got {}".format(metric, v)


if __name__ == "__main__":
    pyunit_utils.standalone_test(grid_metric_accessors)
else:
    grid_metric_accessors()
