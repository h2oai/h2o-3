import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
import pandas as pd
import zipfile
import statsmodels.api as sm
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


def link_functions_gaussian():
  print("Read in prostate data.")
  h2o_data = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate_complete.csv.zip"))
  h2o_data.head()

  sm_data = pd.read_csv(zipfile.ZipFile(pyunit_utils.locate("smalldata/prostate/prostate_complete.csv.zip")).
                        open("prostate_complete.csv")).as_matrix()
  sm_data_response = sm_data[:,9]
  sm_data_features = sm_data[:,1:9]

  print("Testing for family: GAUSSIAN")
  print("Set variables for h2o.")
  myY = "GLEASON"
  myX = ["ID","AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS"]

  print("Create models with canonical link: IDENTITY")
  h2o_model = H2OGeneralizedLinearEstimator(family="gaussian", link="identity",alpha=0.5, Lambda=0)
  h2o_model.train(x=myX, y=myY, training_frame=h2o_data)
  sm_model = sm.GLM(endog=sm_data_response, exog=sm_data_features,
                    family=sm.families.Gaussian(sm.families.links.identity)).fit()

  print("Compare model deviances for link function identity")
  h2o_deviance = h2o_model.residual_deviance() / h2o_model.null_deviance()
  sm_deviance = sm_model.deviance / sm_model.null_deviance
  assert h2o_deviance - sm_deviance < 0.01, "expected h2o to have an equivalent or better deviance measures"



if __name__ == "__main__":
  pyunit_utils.standalone_test(link_functions_gaussian)
else:
  link_functions_gaussian()
