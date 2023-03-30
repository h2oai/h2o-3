import sys

from h2o import H2OFrame

sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
# import statsmodels.api as sm
from sklearn.metrics import log_loss

def glm_mean_residual_deviance():

  cars = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))#.dropna() #.fillna(method="ffill")
  s = cars[0].runif()
  train = cars[s > 0.2]
  valid = cars[s <= 0.2]
  train_pd = train.as_data_frame(use_pandas=True).dropna()
  train = H2OFrame(train_pd)
  predictors = ["displacement","power","weight","acceleration","year"]
  response_col = "economy"
  glm = H2OGeneralizedLinearEstimator(nfolds=3, family="gaussian")
  glm.train(x=predictors, y=response_col, training_frame=train, validation_frame=valid)
  a = glm.aic(train=True, valid=True)
  print(glm.family)
  print(a)
  
  # glm = H2OGeneralizedLinearEstimator(nfolds=3, family="binomial")
  # glm.train(x=predictors, y=response_col, training_frame=train, validation_frame=valid)
  # a = glm.aic(train=True, valid=True)
  # print(glm.family)
  # print(a)

  glm = H2OGeneralizedLinearEstimator(nfolds=3, family="poisson")
  glm.train(x=predictors, y=response_col, training_frame=train, validation_frame=valid)
  a = glm.aic(train=True, valid=True)
  print(glm.family)
  print(a)

  glm = H2OGeneralizedLinearEstimator(nfolds=3, family="negativebinomial")
  glm.train(x=predictors, y=response_col, training_frame=train, validation_frame=valid)
  a = glm.aic(train=True, valid=True)
  print(glm.family)
  print(a)

  glm = H2OGeneralizedLinearEstimator(nfolds=3, family="gamma")
  glm.train(x=predictors, y=response_col, training_frame=train, validation_frame=valid)
  a = glm.aic(train=True, valid=True)
  print(glm.family)
  print(a)




  # model = sm.OLS(y, x).fit()
  
  from statsmodels.regression.linear_model import GLS
  from statsmodels.tools import add_constant
  from statsmodels.genmod.families.family import Family, Gaussian

  regr = GLS(train_pd[response_col], add_constant(train_pd[predictors])).fit()
  pred = regr.predict(train_pd)
  ll = regr.llf
  aic = regr.aic
  ll = Gaussian().loglike_obs(endog=train_pd[response_col], mu=pred)
  




if __name__ == "__main__":
  pyunit_utils.standalone_test(glm_mean_residual_deviance)
else:
  glm_mean_residual_deviance()
