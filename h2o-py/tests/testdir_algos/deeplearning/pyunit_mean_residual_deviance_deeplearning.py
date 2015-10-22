import sys, os
sys.path.insert(1, os.path.join("..",".."))
import h2o
from tests import pyunit_utils


def deeplearning_mean_residual_deviance():

  cars = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
  s = cars.runif()
  train = cars[s > 0.2]
  valid = cars[s <= 0.2]
  predictors = ["displacement","power","weight","acceleration","year"]
  response_col = "economy"

  from h2o.estimators.deeplearning import H2ODeepLearningEstimator

  dl = H2ODeepLearningEstimator(nfolds=3)
  dl.train(x=predictors,y=response_col,training_frame=train,validation_frame=valid)
  dl_mrd = dl.mean_residual_deviance(train=True,valid=True,xval=True)
  assert isinstance(dl_mrd['train'],float), "Expected training mean residual deviance to be a float, but got " \
                                            "{0}".format(type(dl_mrd['train']))
  assert isinstance(dl_mrd['valid'],float), "Expected validation mean residual deviance to be a float, but got " \
                                            "{0}".format(type(dl_mrd['valid']))
  assert isinstance(dl_mrd['xval'],float), "Expected cross-validation mean residual deviance to be a float, but got " \
                                           "{0}".format(type(dl_mrd['xval']))

if __name__ == "__main__":
  pyunit_utils.standalone_test(deeplearning_mean_residual_deviance)
else:
  deeplearning_mean_residual_deviance()
