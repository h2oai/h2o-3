import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.anovaglm import H2OAnovaGLMEstimator

# Simple test to check correct NA handling skip.
def testFrameTransform():
  train = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate_complete.csv.zip"))
  myY = 'CAPSULE'
  myX = ['AGE','VOL','DCAPS']
  train[10,2] = None
  train[20,7] = None
  # build model choosing skip
  anovaG1 = H2OAnovaGLMEstimator(family='binomial', Lambda=0, missing_values_handling="skip")
  anovaG1.train(x=myX, y=myY, training_frame=train)
  # build model deleting the two rows with missing values
  train.drop([10, 20], axis=0)
  anovaG2 = H2OAnovaGLMEstimator(family='binomial', Lambda=0, missing_values_handling="skip")
  anovaG2.train(x=myX, y=myY, training_frame=train)
  # the two models should be the same, compare the model summaries
  summary1 = anovaG1._model_json['output']['model_summary']
  summary2 = anovaG2._model_json['output']['model_summary']
  pyunit_utils.assert_H2OTwoDimTable_equal_upto(summary1, summary2, summary1.col_header)
  
if __name__ == "__main__":
  pyunit_utils.standalone_test(testFrameTransform)
else:
  testFrameTransform()
