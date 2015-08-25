import sys
sys.path.insert(1, "../../../")
import h2o, tests
import pandas as pd
import statsmodels.api as sm

def prostate(ip,port):

  
  

  # Log.info("Importing prostate.csv data...\n")
  h2o_data = h2o.upload_file(path=h2o.locate("smalldata/logreg/prostate.csv"))
  #prostate.summary()

  sm_data = pd.read_csv(h2o.locate("smalldata/logreg/prostate.csv")).as_matrix()
  sm_data_response = sm_data[:,1]
  sm_data_features = sm_data[:,2:]

  #Log.info(cat("B)H2O GLM (binomial) with parameters:\nX:", myX, "\nY:", myY, "\n"))
  h2o_glm = h2o.glm(y=h2o_data[1], x=h2o_data[2:], family="binomial", n_folds=10, alpha=[0.5])
  h2o_glm.show()

  sm_glm = sm.GLM(endog=sm_data_response, exog=sm_data_features, family=sm.families.Binomial()).fit()

  assert abs(sm_glm.null_deviance - h2o_glm._model_json['output']['training_metrics']['null_deviance']) < 1e-5, "Expected null deviances to be the same"

if __name__ == "__main__":
  tests.run_test(sys.argv, prostate)

