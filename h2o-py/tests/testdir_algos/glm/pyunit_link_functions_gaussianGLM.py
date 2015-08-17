import sys
sys.path.insert(1, "../../../")
import h2o
import pandas as pd
import zipfile
import statsmodels.api as sm

def link_functions_gaussian(ip,port):
    
    

    print("Read in prostate data.")
    h2o_data = h2o.import_file(path=h2o.locate("smalldata/prostate/prostate_complete.csv.zip"))
    h2o_data.head()

    sm_data = pd.read_csv(zipfile.ZipFile(h2o.locate("smalldata/prostate/prostate_complete.csv.zip")).
                          open("prostate_complete.csv")).as_matrix()
    sm_data_response = sm_data[:,9]
    sm_data_features = sm_data[:,1:9]

    print("Testing for family: GAUSSIAN")
    print("Set variables for h2o.")
    myY = "GLEASON"
    myX = ["ID","AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS"]

    print("Create models with canonical link: IDENTITY")
    h2o_model = h2o.glm(x=h2o_data[myX], y=h2o_data[myY], family="gaussian", link="identity",alpha=[0.5], Lambda=[0])
    sm_model = sm.GLM(endog=sm_data_response, exog=sm_data_features,
                      family=sm.families.Gaussian(sm.families.links.identity)).fit()

    print("Compare model deviances for link function identity")
    h2o_deviance = h2o_model.residual_deviance() / h2o_model.null_deviance()
    sm_deviance = sm_model.deviance / sm_model.null_deviance
    assert h2o_deviance - sm_deviance < 0.01, "expected h2o to have an equivalent or better deviance measures"

if __name__ == "__main__":
    h2o.run_test(sys.argv, link_functions_gaussian)
