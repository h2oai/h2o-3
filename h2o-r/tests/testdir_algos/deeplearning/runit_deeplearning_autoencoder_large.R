setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

check.deeplearning_autoencoder <- function() {
     Log.info("Deep Learning Autoencoder MNIST)")

     train_hex = h2o.uploadFile(locate("bigdata/laptop/mnist/train.csv.gz"))
     test_hex  = h2o.uploadFile(locate("bigdata/laptop/mnist/test.csv.gz" ))

     predictors = c(1:784)
     resp = 785
     nfeatures = 20 #number of features (smallest hidden layer)

     # split data into two parts (first part for unsupervised training, second part for supervised training)
     sid <- h2o.runif(train_hex, 1234)
     # split <- h2o.splitFrame(train_hex, 0.5)


     # first part of the data, without labels for unsupervised learning (DL auto-encoder)
     train_unsupervised <- train_hex[sid>=0.5,-resp]
     summary(train_unsupervised)

     # second part of the data, with labels for supervised learning (drf)
     train_supervised <- train_hex[sid<0.5,-resp]
     summary(train_supervised)
     train_supervised_labels <- train_hex[sid<0.5, resp]
     summary(train_supervised_labels)

     # third part of the data: test set
     test <- test_hex[,-resp]
     summary(test)
     test_labels <- test_hex[,resp]
     summary(test_labels)

     # train autoencoder on train_unsupervised
     ae_model <- h2o.deeplearning(x=predictors,
                                  training_frame=train_unsupervised,
                                  activation="Tanh",
                                  autoencoder=T,
                                  hidden=c(nfeatures),
                                  epochs=1,
                                  reproducible=T,#slow - turn off for real problems
                                  seed=1234)

     # convert train_supervised with autoencoder model to lower-dimensional space
     train_supervised_features <- h2o.deepfeatures(ae_model, train_supervised, layer=1)
     summary(train_supervised_features)

     expect_equal(ncol(train_supervised_features), nfeatures)

     myX = c(1:nfeatures)
     myY = nfeatures+1
     # Now train DRF on extracted feature space, first need to add response back
     train_supervised_drf <- h2o.cbind(train_supervised_features, as.factor(train_supervised_labels))
     drf_model <- h2o.randomForest(training_frame=train_supervised_drf, x=myX, y=myY, ntrees=10, seed=1234, min_rows=10)

     # Now test the DRF model on the test set (first need to process into the reduced feature space)
     test_features <- h2o.deepfeatures(ae_model, test, layer=1)
     test_features <- h2o.cbind(test_features,test_labels)
     cm <- h2o.confusionMatrix(drf_model, test_features)
     print(cm)

     expect_equal(cm$Error[11], 0.0814, tolerance = 0.001)

     
}

doTest("Deep Learning AutoEncoder MNIST", check.deeplearning_autoencoder)
