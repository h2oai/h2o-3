import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils


def cars_checkpoint():
    cars = h2o.upload_file(pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    predictors = ["displacement","power","weight","acceleration","year"]
    response_col = "economy"
    distribution = "gaussian"

    # build first model
    from h2o.estimators.gbm import H2OGradientBoostingEstimator
    model1 = H2OGradientBoostingEstimator(ntrees=10,max_depth=2, min_rows=10, distribution=distribution, seed=12345)
    model1.train(x=predictors,y=response_col,training_frame=cars)


    # continue building the model
    model2 = H2OGradientBoostingEstimator(ntrees=model1.actual_params['ntrees']+2, max_depth=model1.actual_params['max_depth'],
                                          min_rows=model1.actual_params['min_rows'], distribution=distribution,checkpoint=model1._id)
    model2.train(x=predictors,y=response_col,training_frame=cars)

    # build complete model without stopping and then continue
    model3 = H2OGradientBoostingEstimator(ntrees=12,max_depth=2, min_rows=10, distribution=distribution, seed=12345)
    model3.train(x=predictors,y=response_col,training_frame=cars)

    # model2 and model3 should have the same model metrics
    assert abs(model2.r2() - model3.r2())<1e-6, "Expected R2: {0}, Actual R2: {1} and they are different.".format(model2.r2(), model3.r2())
    assert abs(model2.mse() - model3.mse())<1e-6, "Expected MSE: {0}, Actual MSE: {1} and they are different.".format(model2.mse(), model3.mse())   

    try:
        model = H2OGradientBoostingEstimator(learn_rate=0.00001,distribution=distribution,
                                             checkpoint=model1._id)
        model.train(x=predictors,y=response_col,training_frame=cars)
        assert False, "Expected model-build to fail because learn_rate not modifiable by checkpoint"
    except EnvironmentError:
        assert True

    #   nbins_cats
    try:

        model = H2OGradientBoostingEstimator(nbins_cats=99,distribution=distribution,
                                             checkpoint=model1._id)
        model.train(x=predictors,y=response_col,training_frame=cars)
        assert False, "Expected model-build to fail because nbins_cats not modifiable by checkpoint"
    except EnvironmentError:
        assert True

    #   balance_classes
    try:
        model = H2OGradientBoostingEstimator(balance_classes=True,distribution=distribution,
                                             checkpoint=model1._id)
        model.train(x=predictors,y=response_col,training_frame=cars)
        assert False, "Expected model-build to fail because balance_classes not modifiable by checkpoint"
    except EnvironmentError:
        assert True

    #   nbins
    try:
        model = H2OGradientBoostingEstimator(nbins=99,distribution=distribution,
                                             checkpoint=model1._id)
        model.train(x=predictors,y=response_col,training_frame=cars)
        assert False, "Expected model-build to fail because nbins not modifiable by checkpoint"
    except EnvironmentError:
        assert True

    #   nfolds
    try:
        model = H2OGradientBoostingEstimator(nfolds=3,distribution=distribution,
                                             checkpoint=model1._id)
        model.train(x=predictors,y=response_col,training_frame=cars)
        assert False, "Expected model-build to fail because nfolds not modifiable by checkpoint"
    except EnvironmentError:
        assert True




if __name__ == "__main__":
    pyunit_utils.standalone_test(cars_checkpoint)
else:
    cars_checkpoint()
