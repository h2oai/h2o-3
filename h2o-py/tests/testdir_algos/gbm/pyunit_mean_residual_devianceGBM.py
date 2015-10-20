import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils




def gbm_mean_residual_deviance():

    cars =  h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    s = cars[0].runif()
    train = cars[s > 0.2]
    valid = cars[s <= 0.2]
    predictors = ["displacement","power","weight","acceleration","year"]
    response_col = "economy"
    gbm = h2o.gbm(x=train[predictors],
                  y=train[response_col],
                  validation_x=valid[predictors],
                  validation_y=valid[response_col],
                  nfolds=3)
    gbm_mrd = gbm.mean_residual_deviance(train=True,valid=True,xval=True)
    assert isinstance(gbm_mrd['train'],float), "Expected training mean residual deviance to be a float, but got " \
                                              "{0}".format(type(gbm_mrd['train']))
    assert isinstance(gbm_mrd['valid'],float), "Expected validation mean residual deviance to be a float, but got " \
                                              "{0}".format(type(gbm_mrd['valid']))
    assert isinstance(gbm_mrd['xval'],float), "Expected cross-validation mean residual deviance to be a float, but got " \
                                             "{0}".format(type(gbm_mrd['xval']))



if __name__ == "__main__":
    pyunit_utils.standalone_test(gbm_mean_residual_deviance)
else:
    gbm_mean_residual_deviance()
