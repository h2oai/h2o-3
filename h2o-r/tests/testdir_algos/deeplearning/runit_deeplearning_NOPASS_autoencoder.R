setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

check.deeplearning_autoencoder <- function(conn) {
     Log.info("Deep Learning Autoencoder MNIST)")

     train_hex = h2o.uploadFile(conn, locate("smalldata/mnist/train.csv.gz"))
     test_hex = h2o.uploadFile(conn, locate("smalldata/mnist/test.csv.gz"))

     predictors = c(1:784)
     resp = 785
     nfeatures = 20 #number of features (smallest hidden layer)

     # split data into two parts (first part for unsupervised training, second part for supervised training)
     split <- h2o.splitFrame(train_hex, 0.5)

     # first part of the data, without labels for unsupervised learning (DL auto-encoder)
     train_unsupervised <- split[[1]][,-resp]
     summary(train_unsupervised)

     # second part of the data, with labels for supervised learning (Random Forest)
     train_supervised <- split[[2]][,-resp]
     summary(train_supervised)
     train_supervised_labels <- split[[2]][,resp]
     summary(train_supervised_labels)

     # third part of the data: test set
     test <- test_hex[,-resp]
     summary(test)
     test_labels <- test_hex[,resp]
     summary(test_labels)

     # train autoencoder on train_unsupervised
     ae_model <- h2o.deeplearning(x=predictors,
                                  y=42, #ignored (pick any non-constant predictor)
                                  training_frame=train_unsupervised,
                                  activation="Tanh",
                                  autoencoder=T,
                                  hidden=c(nfeatures),
                                  epochs=1,
                                  reproducible=T,#slow - turn off for real problems
                                  seed=1234)

     # convert train_supervised with autoencoder model to lower-dimensional space
     train_supervised_features <- h2o.deepfeatures(train_supervised, ae_model, layer=1)
     summary(train_supervised_features)

     checkTrue(ncol(train_supervised_features) == nfeatures, "Dimensionality of reconstruction is wrong!")

     # Now train RF on extracted feature space, first need to add response back
     train_supervised_rf <- cbind(train_supervised_features, train_supervised_labels)
     rf_model <- h2o.randomForest(data=train_supervised_rf, x=c(1:nfeatures), y=nfeatures+1, ntree=10, importance=T, seed=1234)

     # Now test the RF model on the test set (first need to process into the reduced feature space)
     test_features <- h2o.deepfeatures(test, ae_model, layer=1)
     test_preds <- h2o.predict(rf_model, test_features)
     cm <- h2o.confusionMatrix(test_preds[,1], test_labels)
     cm

     checkTrue(cm[length(cm)] == 0.104) #10% test set error

     testEnd()
}

doTest("Deep Learning AutoEncoder MNIST", check.deeplearning_autoencoder)

