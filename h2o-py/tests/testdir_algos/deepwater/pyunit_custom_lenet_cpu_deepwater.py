from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators.deepwater import H2ODeepWaterEstimator

def lenet(num_classes):
    import mxnet as mx
    data = mx.symbol.Variable('data')
    # first conv
    conv1 = mx.symbol.Convolution(data=data, kernel=(5,5), num_filter=20)
    tanh1 = mx.symbol.Activation(data=conv1, act_type="tanh")
    pool1 = mx.symbol.Pooling(data=tanh1, pool_type="max",
                              kernel=(2,2), stride=(2,2))
    # second conv
    conv2 = mx.symbol.Convolution(data=pool1, kernel=(5,5), num_filter=50)
    tanh2 = mx.symbol.Activation(data=conv2, act_type="tanh")
    pool2 = mx.symbol.Pooling(data=tanh2, pool_type="max",
                              kernel=(2,2), stride=(2,2))
    # first fullc
    flatten = mx.symbol.Flatten(data=pool2)
    fc1 = mx.symbol.FullyConnected(data=flatten, num_hidden=500)
    tanh3 = mx.symbol.Activation(data=fc1, act_type="tanh")
    # second fullc
    fc2 = mx.symbol.FullyConnected(data=tanh3, num_hidden=num_classes)
    # loss
    lenet = mx.symbol.SoftmaxOutput(data=fc2, name='softmax')
    return lenet


def deepwater_custom_lenet():
  if not H2ODeepWaterEstimator.available(): return

  frame = h2o.import_file(pyunit_utils.locate("bigdata/laptop/deepwater/imagenet/cat_dog_mouse.csv"))
  print(frame.head(5))
  nclasses = frame[1].nlevels()[0]

  print("Creating the model architecture from scratch using the MXNet Python API")
  lenet(nclasses).save("/tmp/symbol_lenet-py.json")

  print("Importing the model architecture for training in H2O")
  model = H2ODeepWaterEstimator(epochs=50, learning_rate=1e-3, mini_batch_size=32,
                                network='user', network_definition_file="/tmp/symbol_lenet-py.json", image_shape=[28,28], channels=1,
                                score_interval=0, train_samples_per_iteration=1000,
                                gpu=False)
  model.train(x=[0],y=1, training_frame=frame)
  model.show()
  error = model.model_performance(train=True).mean_per_class_error()
  assert error < 0.1, "mean classification error is too high : " + str(error)

if __name__ == "__main__":
  pyunit_utils.standalone_test(deepwater_custom_lenet)
else:
  deepwater_custom_lenet()
