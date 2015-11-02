import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils


from h2o.estimators.deeplearning import H2OAutoEncoderEstimator
from h2o.estimators.random_forest import H2ORandomForestEstimator


def deeplearning_autoencoder():

  resp = 784
  nfeatures = 20  # number of features (smallest hidden layer)

  train_hex = h2o.upload_file(pyunit_utils.locate("bigdata/laptop/mnist/train.csv.gz"))
  train_hex[resp] = train_hex[resp].asfactor()

  test_hex = h2o.upload_file(pyunit_utils.locate("bigdata/laptop/mnist/test.csv.gz"))
  test_hex[resp] = test_hex[resp].asfactor()

  # split data into two parts
  sid = train_hex[0].runif(1234)

  # unsupervised data for autoencoder
  train_unsupervised = train_hex[sid >= 0.5]
  train_unsupervised.drop(resp)
  train_unsupervised.describe()


  # supervised data for drf
  train_supervised = train_hex[sid < 0.5]
  train_supervised.describe()

  # train autoencoder
  ae_model = H2OAutoEncoderEstimator(activation="Tanh",
                                     hidden=[nfeatures],
                                     epochs=1,
                                     reproducible=True,
                                     seed=1234)

  ae_model.train(range(resp), training_frame=train_supervised)

  # conver train_supervised with autoencoder to lower-dimensional space
  train_supervised_features = ae_model.deepfeatures(train_supervised[0:resp], 0)

  assert train_supervised_features.ncol == nfeatures, "Dimensionality of reconstruction is wrong!"

  train_supervised_features = train_supervised_features.cbind(train_supervised[resp])

  # Train DRF on extracted feature space
  drf_model = H2ORandomForestEstimator(ntrees=10, min_rows=10, seed=1234)
  drf_model.train(x=range(20), y=train_supervised_features.ncol-1, training_frame=train_supervised_features)

  # Test the DRF model on the test set (processed through deep features)
  test_features = ae_model.deepfeatures(test_hex[0:resp], 0)
  test_features = test_features.cbind(test_hex[resp])

  # Confusion Matrix and assertion
  cm = drf_model.confusion_matrix(test_features)
  cm.show()

  # 10% error +/- 0.001
  assert abs(cm.cell_values[10][10] - 0.081) <= 0.01, "Error. Expected 0.081, but got {0}".format(cm.cell_values[10][10])


if __name__ == "__main__":
    pyunit_utils.standalone_test(deeplearning_autoencoder)
else:
    deeplearning_autoencoder()
