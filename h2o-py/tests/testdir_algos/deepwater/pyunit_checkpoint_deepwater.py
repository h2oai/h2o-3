from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators.deepwater import H2ODeepWaterEstimator

def deepwater_checkpoint():
  if not H2ODeepWaterEstimator.available(): return

  ## build a model
  #frame = h2o.import_file(pyunit_utils.locate("bigdata/laptop/deepwater/imagenet/cat_dog_mouse.csv"))
  frame = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
  frame.drop(0)
  frame[1] = frame[1].asfactor()
  print(frame.head(5))
  model = H2ODeepWaterEstimator(epochs=50, learning_rate=1e-5, stopping_rounds=0, score_duty_cycle=1, train_samples_per_iteration=-1, score_interval=0)
  model.train(y=1, training_frame=frame)

  ## save the model
  model_path = h2o.save_model(model)

  ## delete everything - simulate cluster shutdown and restart
  h2o.remove_all()

  ## reimport the model and the frame
  model = h2o.load_model(model_path)
  #frame = h2o.import_file(pyunit_utils.locate("bigdata/laptop/deepwater/imagenet/cat_dog_mouse.csv"))
  frame = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
  frame.drop(0)
  frame[1] = frame[1].asfactor()
  
  ## delete the checkpoint file
  os.remove(model_path)

  ## continue training
  model2 = H2ODeepWaterEstimator(epochs=100, learning_rate=1e-5, stopping_rounds=0,score_duty_cycle=1, train_samples_per_iteration=-1, score_interval=0, checkpoint=model.model_id)
  model2.train(y=1, training_frame=frame)
  model2.show()

if __name__ == "__main__":
  pyunit_utils.standalone_test(deepwater_checkpoint)
else:
  deepwater_checkpoint()
