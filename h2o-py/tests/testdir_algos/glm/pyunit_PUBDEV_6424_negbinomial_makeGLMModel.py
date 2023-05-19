import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

def test_negBinomial_makeGLMModel():
  print("Read in prostate data.")
  h2o_data = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate_complete.csv.zip"))
  print("Testing for family: Negative Binomial")
  print("Set variables for h2o.")
  myY = "GLEASON"
  myX = ["ID","AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS"]

  thetas = [0.000000001, 0.01, 0.1, 0.5, 1]
  for thetaO in thetas:
      h2o_model_log = H2OGeneralizedLinearEstimator(family="negativebinomial", link="log",alpha=0.5, Lambda=0.0001,
                                                    theta=thetaO)
      h2o_model_log.train(x=myX, y=myY, training_frame=h2o_data)
      predictModel = h2o_model_log.predict(h2o_data)
      r = H2OGeneralizedLinearEstimator.getGLMRegularizationPath(h2o_model_log)
      makeModel = H2OGeneralizedLinearEstimator.makeGLMModel(model=h2o_model_log,coefs=r['coefficients'][0]) # model generated from setting coefficients to model
      predictMake = makeModel.predict(h2o_data)
      pyunit_utils.compare_frames_local(predictModel, predictMake, prob=1)


if __name__ == "__main__":
  pyunit_utils.standalone_test(test_negBinomial_makeGLMModel)
else:
  test_negBinomial_makeGLMModel()
