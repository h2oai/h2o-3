import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils


def mnist_many_cols_gbm_large():
  train = h2o.import_file(path=pyunit_utils.locate("bigdata/laptop/mnist/train.csv.gz"))
  train.tail()

  from h2o.estimators.gbm import H2OGradientBoostingEstimator
  gbm_mnist = H2OGradientBoostingEstimator(ntrees=1,
                                           max_depth=1,
                                           min_rows=10,
                                           learn_rate=0.01)
  gbm_mnist.train(x=range(784), y=784, training_frame=train)
  gbm_mnist.show()


if __name__ == "__main__":
  pyunit_utils.standalone_test(mnist_many_cols_gbm_large)
else:
  mnist_many_cols_gbm_large()
