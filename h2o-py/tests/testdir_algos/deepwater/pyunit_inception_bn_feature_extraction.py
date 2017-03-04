from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators.deepwater import H2ODeepWaterEstimator

import urllib

def deepwater_inception_bn_feature_extraction():
  if not H2ODeepWaterEstimator.available(): return

  frame = h2o.import_file(pyunit_utils.locate("bigdata/laptop/deepwater/imagenet/cat_dog_mouse.csv"))
  print(frame.head(5))
  nclasses = frame[1].nlevels()[0]

  print("Downloading the model")
  if not os.path.exists("model.json"):
    urllib.urlretrieve ("https://raw.githubusercontent.com/h2oai/deepwater/master/mxnet/src/main/resources/deepwater/backends/mxnet/models/Inception/Inception_BN-symbol.json", "model.json")
  if not os.path.exists("model.params"):
    urllib.urlretrieve ("https://raw.githubusercontent.com/h2oai/deepwater/master/mxnet/src/main/resources/deepwater/backends/mxnet/models/Inception/Inception_BN-0039.params", "model.params")
  if not os.path.exists("mean_224.nd"):
    urllib.urlretrieve ("https://raw.githubusercontent.com/h2oai/deepwater/master/mxnet/src/main/resources/deepwater/backends/mxnet/models/Inception/mean_224.nd", "mean_224.nd")

  print("Importing the model architecture for training in H2O")
  model = H2ODeepWaterEstimator(epochs=0, ## no training - just load the state - NOTE: training for this 3-class problem wouldn't work since the model has 1k classes
                                mini_batch_size=32, ## mini-batch size is used for scoring
                                ## all parameters below are needed
                                network='user', 
                                network_definition_file=os.getcwd() + "/model.json", 
                                network_parameters_file=os.getcwd() + "/model.params", 
                                mean_image_file=os.getcwd() + "/mean_224.nd",
                                image_shape=[224,224],
                                channels=3
  )
  model.train(x=[0],y=1, training_frame=frame) ## must call train() to initialize the model, but it isn't training

  ## Extract deep features from final layer before going into Softmax.
  extracted_features = model.deepfeatures(frame, "global_pool_output")
  extracted_features2 = model.deepfeatures(frame, "conv_5b_double_3x3_1_output")

  ## Cleanup (first)
  os.remove("model.json")
  os.remove("model.params")
  os.remove("mean_224.nd")

  print(extracted_features.ncol)
  assert extracted_features.ncol == 1024

  print(extracted_features2.ncol)
  assert extracted_features2.ncol == 10976

if __name__ == "__main__":
  pyunit_utils.standalone_test(deepwater_inception_bn_feature_extraction)
else:
  deepwater_inception_bn_feature_extraction()
