import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.anovaglm import H2OANOVAGLMEstimator

# Simple test to check correct frame transformation
def testFrameTransform():
  train = h2o.import_file(path=pyunit_utils.locate("smalldata/anovaGlm/Moore.csv"))
  answer = h2o.import_file(path=pyunit_utils.locate("smalldata/anovaGlm/MooreTransformed.csv"))
  y = 'conformity'
  x = ['fcategory', 'partner.status']

  model = H2OANOVAGLMEstimator(family='gaussian', lambda_=0, save_transformed_framekeys=True)
  model.train(x=x, y=y, training_frame=train)
  transformFrame = h2o.get_frame(model._model_json["output"]["transformed_columns_key"])
  pyunit_utils.compare_frames_local(answer[['fcategory1', 'fcategory2', 'partner.status1', 
                                            'fcategory1:partner.status1', 'fcategory2:partner.status1']], 
                                    transformFrame[['fcategory_high', 'fcategory_low', 'partner.status_high', 
                                                    'fcategory_high:partner.status_high', 
                                                    'fcategory_low:partner.status_high']], prob=1)
  
if __name__ == "__main__":
  pyunit_utils.standalone_test(testFrameTransform)
else:
  testFrameTransform()
