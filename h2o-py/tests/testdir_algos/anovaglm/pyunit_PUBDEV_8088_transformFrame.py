import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.anovaglm import H2OAnovaGLMEstimator

# Simple test to check correct frame transformation
def testFrameTransform():
  train = h2o.import_file(path=pyunit_utils.locate("smalldata/anovaglm/Moore.csv"))
  answer = h2o.import_file(path=pyunit_utils.locate("smalldata/anovaglm/MooreTransformed.csv"))
  myY = 'conformity'
  myX = ['fcategory', 'partner.status']

  anovaG = H2OAnovaGLMEstimator(family='gaussian', Lambda=0, save_transformed_framekeys=True)
  anovaG.train(x=myX, y=myY, training_frame=train)
  transformFrame = h2o.get_frame(anovaG._model_json["output"]["transformed_columns_key"])
  pyunit_utils.compare_frames_local(answer[['fcategory1', 'fcategory2', 'partner.status1', 
                                            'fcategory1:partner.status1', 'fcategory2:partner.status1']], 
                                    transformFrame[['fcategory_high', 'fcategory_low', 'partner.status_high', 
                                                    'fcategory_high:partner.status_high', 
                                                    'fcategory_low:partner.status_high']], prob=1)
  
if __name__ == "__main__":
  pyunit_utils.standalone_test(testFrameTransform)
else:
  testFrameTransform()
