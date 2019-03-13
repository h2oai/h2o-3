from __future__ import division
from __future__ import print_function
from past.utils import old_div
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
import pandas as pd
import zipfile
import statsmodels.api as sm
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

def link_functions_negbinomial():

  print("Read in prostate data.")
  h2o_data = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate_complete.csv.zip"))

  sm_data = pd.read_csv(zipfile.ZipFile(pyunit_utils.locate("smalldata/prostate/prostate_complete.csv.zip")).
                        open("prostate_complete.csv")).as_matrix()
  sm_data_response = sm_data[:,9]
  sm_data_features = sm_data[:,1:9]

  print("Testing for family: Negative Binomial")
  print("Set variables for h2o.")
  myY = "GLEASON"
  myX = ["ID","AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS"]

  thetas = [0.000000001, 0.01, 0.1, 0.5, 1]
  for thetaO in thetas:
      print("Create statsmodel model with canonical link: LOG")
      sm_model_log = sm.GLM(endog=sm_data_response, exog=sm_data_features,
                            family=sm.families.NegativeBinomial(sm.families.links.log, thetaO)).fit()
      print("Create h2o model with canonical link: LOG")
      h2o_model_log = H2OGeneralizedLinearEstimator(family="negativebinomial", link="log",alpha=0.5, Lambda=0,
                                                    theta=thetaO)
      h2o_model_log.train(x=myX, y=myY, training_frame=h2o_data)
      print("Comparing H2O model and Python model with log link and theta={0}".format(thetaO))
      compareModels(h2o_model_log, sm_model_log)
    
      print("Create statsmodel model with canonical link: identity")
      sm_model_identity = sm.GLM(endog=sm_data_response, exog=sm_data_features,
                          family=sm.families.NegativeBinomial(sm.families.links.identity, thetaO)).fit()
      print("Create h2o model with canonical link: identity")
      h2o_model_identity = H2OGeneralizedLinearEstimator(family="negativebinomial", link="identity",alpha=0.5, Lambda=0,
                                                  theta=thetaO)
      h2o_model_identity.train(x=myX, y=myY, training_frame=h2o_data)
      print("Comparing H2O model and Python model with identity link and theta = ".format(thetaO))
      compareModels(h2o_model_identity, sm_model_identity)

def compareModels(h2oModel, smModel):
    h2o_deviance = old_div(h2oModel.residual_deviance(), h2oModel.null_deviance())
    sm_deviance = old_div(smModel.deviance, smModel.null_deviance)
    difference = h2o_deviance-sm_deviance
    print("Difference between h2o Model and sm Model deviance measure: {0}".format(difference))
    
    assert difference < 0.01, "expected h2o to have an equivalent or better deviance measures"


if __name__ == "__main__":
  pyunit_utils.standalone_test(link_functions_negbinomial)
else:
  link_functions_negbinomial()
