from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from h2o.automl import H2OAutoML
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm

def testSlowGLM():
    train = h2o.import_file("/Users/wendycwong/temp/slowGLMDataset")
    x = train.names
    x.remove('job')
    alphas = [0,0.2,0.4,0.6,0.8,1.0]
    glmModel = glm(lambda_search=True, nlambdas=30, alpha = alphas, family = 'multinomial')    
    glmModel.train(training_frame=train, x= x, y='job')
    # aml = H2OAutoML(max_runtime_secs=360)
    # aml.train(training_frame=train, y='job')
    print("Done")
 
if __name__ == "__main__":
  pyunit_utils.standalone_test(testSlowGLM)
else:
    testSlowGLM()
