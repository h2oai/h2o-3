import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils

from h2o.estimators.gbm import H2OGradientBoostingEstimator

def pubdev_random_cv():

    cars =  h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    response_col = "economy"
    distribution = "gaussian"
    predictors = ["displacement","power","weight","acceleration","year"]

    gbm1 = H2OGradientBoostingEstimator(nfolds=3,distribution=distribution,fold_assignment="Random")
    gbm2 = H2OGradientBoostingEstimator(nfolds=3,distribution=distribution,fold_assignment="Random")
    gbm1.train(y=response_col,x=predictors,training_frame=cars)
    gbm2.train(y=response_col,x=predictors,training_frame=cars)

    mse1 = gbm1.mse(xval=True)
    mse2 = gbm2.mse(xval=True)
    assert mse1 != mse2, "The first model has an MSE of {0} and the second model has an MSE of {1}. Expected the " \
                         "first to be different from the second.".format(mse1, mse2)


if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_random_cv)
else:
    pubdev_random_cv()
