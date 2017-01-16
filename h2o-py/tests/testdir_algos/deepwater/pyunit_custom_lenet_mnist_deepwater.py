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
    conv1 = mx.symbol.Convolution(data=data, kernel=(4,4), num_filter=32)
    relu1 = mx.symbol.Activation(data=conv1, act_type="relu")
    pool1 = mx.symbol.Pooling(data=relu1, pool_type="max", kernel=(2,2), stride=(2,2))
    # second conv
    conv2 = mx.symbol.Convolution(data=pool1, kernel=(3,3), num_filter=16)
    relu2 = mx.symbol.Activation(data=conv2, act_type="relu")
    pool2 = mx.symbol.Pooling(data=relu2, pool_type="max", kernel=(2,2), stride=(2,2))
    drop = mx.symbol.Dropout(data=pool2, p=0.2)

    # first fullc
    flatten = mx.symbol.Flatten(data=drop)
    fc1 = mx.symbol.FullyConnected(data=flatten, num_hidden=128)
    relu3 = mx.symbol.Activation(data=fc1, act_type="relu")

    fc2 = mx.symbol.FullyConnected(data=relu3, num_hidden=64)
    relu4 = mx.symbol.Activation(data=fc2, act_type="relu")

    # second fullc
    fc3 = mx.symbol.FullyConnected(data=relu4, num_hidden=num_classes)
    net = mx.symbol.SoftmaxOutput(data=fc3, name='softmax')
    return net

def deepwater_custom_lenet_mnist():
  if not H2ODeepWaterEstimator.available(): return

  train = h2o.import_file(pyunit_utils.locate("bigdata/laptop/mnist/train.csv.gz"))
  test = h2o.import_file(pyunit_utils.locate("bigdata/laptop/mnist/test.csv.gz"))

  predictors = list(range(0,784))
  resp = 784
  train[resp] = train[resp].asfactor()
  test[resp] = test[resp].asfactor()
  nclasses = train[resp].nlevels()[0]

  print("Creating the lenet model architecture from scratch using the MXNet Python API")
  lenet(nclasses).save("/tmp/symbol_lenet-py.json")

  print("Importing the lenet model architecture for training in H2O")
  model = H2ODeepWaterEstimator(epochs=10,
                                learning_rate=0.05, 
                                learning_rate_annealing=1e-5, 
                                momentum_start=0.9,
                                momentum_stable=0.9,
                                mini_batch_size=128,
                                train_samples_per_iteration=0,
                                score_duty_cycle=0,
                                stopping_rounds=0,
                                ignore_const_cols=False,
                                network_definition_file="/tmp/symbol_lenet-py.json",
				image_shape=[28,28],
                                channels=1)
                                
  model.train(x=predictors,y=resp, training_frame=train)
  model.show()
  print(model.model_performance(valid=True))
  error = model.model_performance(test).mean_per_class_error()
  assert error < 0.1, "mean classification error on validation set is too high : " + str(error)

if __name__ == "__main__":
  pyunit_utils.standalone_test(deepwater_custom_lenet_mnist)
else:
  deepwater_custom_lenet_mnist()
