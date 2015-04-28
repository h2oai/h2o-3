import os, sys
sys.path.insert(1,"../../../")
import h2o

def deeplearning_autoencoder(ip, port):
  h2o.init(ip, port)

  resp = 784
  nfeatures = 20 # number of features (smallest hidden layer)


  train_hex = h2o.import_frame(h2o.locate("bigdata/laptop/mnist/train.csv.gz"))
  test_hex = h2o.import_frame(h2o.locate("bigdata/laptop/mnist/test.csv.gz"))

  # split data into two parts
  sid = train_hex[1].runif(1234)

  # unsupervised data for autoencoder
  train_unsupervised = train_hex[sid >= 0.5]
  train_unsupervised.describe()

  # supervised data for drf
  train_supervised = train_hex[sid < 0.5]
  train_supervised.describe()

  # train autoencoder
  ae_model = h2o.deeplearning(x=train_unsupervised.drop(resp),
                              y=train_unsupervised[resp], #ignored (pick any non-constant)
                              activation="Tanh",
                              autoencoder=True,
                              hidden=[nfeatures],
                              epochs=1,
                              reproducible=True, #slow, turn off for real problems
                              seed=1234)

  # conver train_supervised with autoencoder to lower-dimensional space
  train_supervised_features = ae_model.deepfeatures(train_supervised, 0)
  train_supervised_features.describe()

  assert train_supervised_features.ncol() == nfeatures, "Dimensionality of reconstruction is wrong!"

  # Train DRF on extracted feature space
  drf_model = h2o.random_forest(x=train_supervised_features,
                                y=train_supervised[resp].asfactor(),
                                ntrees=10,
                                seed=1234)

  # Test the DRF model on the test set (processed through deep features)
  test_features = ae_model.deepfeatures(test_hex.drop(resp), 0)
  test_features.cbind(test_hex[resp])

  # Confusion Matrix and assertion
  cm = drf_model.confusion_matrix(test_features)
  cm.show()

  # 10% error +/- 0.001
  assert abs(cm["Totals", "Error"] - 0.1038) < 0.001, "Error not as expected"

if __name__ == '__main__':
    h2o.run_test(sys.argv, deeplearning_autoencoder)

