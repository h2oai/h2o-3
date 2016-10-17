from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators.deepwater import H2ODeepWaterEstimator

def cnn(num_classes):
    import mxnet as mx
    data = mx.symbol.Variable('data')

    inputdropout = mx.symbol.Dropout(data=data, p=0.1)

    # first conv
    conv1 = mx.symbol.Convolution(data=data, kernel=(5,5), num_filter=50)
    tanh1 = mx.symbol.Activation(data=conv1, act_type="relu")
    pool1 = mx.symbol.Pooling(data=tanh1, pool_type="max", kernel=(3,3), stride=(2,2))
    # second conv
    conv2 = mx.symbol.Convolution(data=pool1, kernel=(5,5), num_filter=100)
    tanh2 = mx.symbol.Activation(data=conv2, act_type="relu")
    pool2 = mx.symbol.Pooling(data=tanh2, pool_type="max", kernel=(3,3), stride=(2,2))
    # first fullc
    flatten = mx.symbol.Flatten(data=pool2)
    fc1 = mx.symbol.FullyConnected(data=flatten, num_hidden=1024)
    relu3 = mx.symbol.Activation(data=fc1, act_type="relu")
    inputdropout = mx.symbol.Dropout(data=fc1, p=0.5)
    # second fullc
    flatten = mx.symbol.Flatten(data=relu3)
    fc2 = mx.symbol.FullyConnected(data=flatten, num_hidden=1024)
    relu4 = mx.symbol.Activation(data=fc2, act_type="relu")
    inputdropout = mx.symbol.Dropout(data=fc2, p=0.5)
    # third fullc
    fc3 = mx.symbol.FullyConnected(data=relu4, num_hidden=num_classes)
    # loss
    cnn = mx.symbol.SoftmaxOutput(data=fc3, name='softmax')
    return cnn


def deepwater_custom_cnn_mnist():
  if not H2ODeepWaterEstimator.available(): return

  train = h2o.import_file(pyunit_utils.locate("bigdata/laptop/mnist/train.csv.gz"))
  test = h2o.import_file(pyunit_utils.locate("bigdata/laptop/mnist/test.csv.gz"))

  predictors = list(range(0,784))
  resp = 784
  train[resp] = train[resp].asfactor()
  test[resp] = test[resp].asfactor()
  nclasses = train[resp].nlevels()[0]

  print("Creating the cnn model architecture from scratch using the MXNet Python API")
  cnn(nclasses).save("/tmp/symbol_custom-py.json")

  print("Importing the cnn model architecture for training in H2O")
  model = H2ODeepWaterEstimator(epochs=100, learning_rate=1e-3, mini_batch_size=64,
                                network='user', network_definition_file="/tmp/symbol_custom-py.json",
				image_shape=[28,28], channels=1)
                                
  model.train(x=predictors,y=resp, training_frame=train, validation_frame=test)
  model.show()
  error = model.model_performance(valid=True).mean_per_class_error()
  assert error < 0.1, "mean classification error on validation set is too high : " + str(error)

if __name__ == "__main__":
  pyunit_utils.standalone_test(deepwater_custom_cnn_mnist)
else:
  deepwater_custom_cnn_mnist()
