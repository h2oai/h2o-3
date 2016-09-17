#!/usr/bin/env python
# -*- encoding: utf-8 -*-
import h2o
from h2o.estimators.deeplearning import H2OAutoEncoderEstimator
from h2o.estimators.deeplearning import H2ODeepLearningEstimator
from h2o.estimators.random_forest import H2ORandomForestEstimator
from tests import pyunit_utils


def deeplearning_autoencoder():
    resp = 784
    nfeatures = 20  # number of features (smallest hidden layer)

    train_hex = h2o.upload_file(pyunit_utils.locate("bigdata/laptop/mnist/train.csv.gz"))
    train_hex[resp] = train_hex[resp].asfactor()

    test_hex = h2o.upload_file(pyunit_utils.locate("bigdata/laptop/mnist/test.csv.gz"))
    test_hex[resp] = test_hex[resp].asfactor()

    # split data into two parts
    sid = train_hex[0].runif(0)

    # unsupervised data for autoencoder
    train_unsupervised = train_hex[sid >= 0.5]
    train_unsupervised.pop(resp)
    #train_unsupervised.describe()


    # supervised data for drf
    train_supervised = train_hex[sid < 0.5]
    #train_supervised.describe()

    # train autoencoder
    ae_model = H2OAutoEncoderEstimator(activation="Tanh",
                                       hidden=[nfeatures],
                                       model_id="ae_model",
                                       epochs=1,
                                       ignore_const_cols=False,
                                       reproducible=True,
                                       seed=1234)

    ae_model.train(list(range(resp)), training_frame=train_unsupervised)

    # convert train_supervised with autoencoder to lower-dimensional space
    train_supervised_features = ae_model.deepfeatures(train_supervised[0:resp], 0)

    assert train_supervised_features.ncol == nfeatures, "Dimensionality of reconstruction is wrong!"

    train_supervised_features = train_supervised_features.cbind(train_supervised[resp])

    # Train DRF on extracted feature space
    drf_model = H2ORandomForestEstimator(ntrees=10, min_rows=10, seed=1234)
    drf_model.train(x=list(range(20)), y=train_supervised_features.ncol - 1, training_frame=train_supervised_features)

    # Test the DRF model on the test set (processed through deep features)
    test_features = ae_model.deepfeatures(test_hex[0:resp], 0)
    test_features = test_features.cbind(test_hex[resp])

    # Confusion Matrix and assertion
    cm = drf_model.confusion_matrix(test_features)
    cm.show()

    # 8.8% error +/- 1%
    #compare to runit_deeplearning_autoencoder_large.py
    assert abs(cm.cell_values[10][10] - 0.088) < 0.01, \
        "Error. Expected 0.088, but got {0}".format(cm.cell_values[10][10])

    # Another usecase: Use pretrained unsupervised autoencoder model to initialize a supervised Deep Learning model
    pretrained_model = H2ODeepLearningEstimator(activation="Tanh", hidden=[nfeatures], epochs=1, reproducible=True,
                                                seed=1234, ignore_const_cols=False, pretrained_autoencoder="ae_model")
    pretrained_model.train(list(range(resp)), resp, training_frame=train_supervised, validation_frame=test_hex)
    print(pretrained_model.logloss(train=False, valid=True))

    model_from_scratch = H2ODeepLearningEstimator(activation="Tanh", hidden=[nfeatures], epochs=1, reproducible=True,
                                                  seed=1234, ignore_const_cols=False)
    model_from_scratch.train(list(range(resp)), resp, training_frame=train_supervised, validation_frame=test_hex)
    print(model_from_scratch.logloss(train=False, valid=True))

    assert pretrained_model.logloss(train=False, valid=True) < model_from_scratch.logloss(train=False, valid=True), \
        "Error. Pretrained model should lead to lower logloss than training from scratch."

if __name__ == "__main__":
    pyunit_utils.standalone_test(deeplearning_autoencoder)
else:
    deeplearning_autoencoder()
