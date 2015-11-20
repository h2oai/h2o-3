import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils


def deeplearning_autoencoder():


    resp = 784
    nfeatures = 20 # number of features (smallest hidden layer)

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
    ae_model = h2o.deeplearning(x=train_unsupervised[0:resp],
                                activation="Tanh",
                                autoencoder=True,
                                hidden=[nfeatures],
                                epochs=1,
                                reproducible=True, #slow, turn off for real problems
                                seed=1234)

    # conver train_supervised with autoencoder to lower-dimensional space
    train_supervised_features = ae_model.deepfeatures(train_supervised[0:resp], 0)

    assert train_supervised_features.ncol == nfeatures, "Dimensionality of reconstruction is wrong!"

    # Train DRF on extracted feature space
    drf_model = h2o.random_forest(x=train_supervised_features[0:20],
                                  y=train_supervised[resp],
                                  ntrees=10,
                                  min_rows=10,
                                  seed=1234)

    # Test the DRF model on the test set (processed through deep features)
    test_features = ae_model.deepfeatures(test_hex[0:resp], 0)
    test_features = test_features.cbind(test_hex[resp])

    # Confusion Matrix and assertion
    cm = drf_model.confusion_matrix(test_features)
    cm.show()

    # 10% error +/- 0.001
    assert abs(cm.cell_values[10][10] - 0.081) < 0.001, "Error. Expected 0.081, but got {0}".format(cm.cell_values[10][10])

if __name__ == "__main__":
  pyunit_utils.standalone_test(deeplearning_autoencoder)
else:
  deeplearning_autoencoder()


