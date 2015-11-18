import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils



import pandas as pd
import zipfile
import statsmodels.api as sm

def link_functions_gamma():
	
	

	print("Read in prostate data.")
	h2o_data = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate_complete.csv.zip"))
	h2o_data.head()

	sm_data = pd.read_csv(zipfile.ZipFile(pyunit_utils.locate("smalldata/prostate/prostate_complete.csv.zip")).
							  open("prostate_complete.csv")).as_matrix()
	sm_data_response = sm_data[:,5]
	sm_data_features = sm_data[:,[1,2,3,4,6,7,8,9]]

	print("Testing for family: GAMMA")
	print("Set variables for h2o.")
	myY = "DPROS"
	myX = ["ID","AGE","RACE","GLEASON","DCAPS","PSA","VOL","CAPSULE"]

	print("Create models with canonical link: INVERSE")
	h2o_model_in = h2o.glm(x=h2o_data[myX], y=h2o_data[myY], family="gamma", link="inverse",alpha=[0.5], Lambda=[0])
	sm_model_in = sm.GLM(endog=sm_data_response, exog=sm_data_features,
						 family=sm.families.Gamma(sm.families.links.inverse_power)).fit()

	print("Compare model deviances for link function inverse")
	h2o_deviance_in = h2o_model_in.residual_deviance() / h2o_model_in.null_deviance()
	sm_deviance_in = sm_model_in.deviance / sm_model_in.null_deviance
	assert h2o_deviance_in - sm_deviance_in < 0.01, "expected h2o to have an equivalent or better deviance measures"

	print("Create models with canonical link: LOG")
	h2o_model_log = h2o.glm(x=h2o_data[myX], y=h2o_data[myY], family="gamma", link="log",alpha=[0.5], Lambda=[0])
	sm_model_log = sm.GLM(endog=sm_data_response, exog=sm_data_features,
						  family=sm.families.Gamma(sm.families.links.log)).fit()

	print("Compare model deviances for link function log")
	h2o_deviance_log = h2o_model_log.residual_deviance() / h2o_model_log.null_deviance()
	sm_deviance_log = sm_model_log.deviance / sm_model_log.null_deviance
	assert h2o_deviance_log - sm_deviance_log < 0.01, "expected h2o to have an equivalent or better deviance measures"




if __name__ == "__main__":
    pyunit_utils.standalone_test(link_functions_gamma)
else:
	link_functions_gamma()
