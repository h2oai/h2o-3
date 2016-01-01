setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



check.deeplearning_autoencoder <- function() {
     h2oTest.logInfo("Deep Learning Autoencoder MNIST)")

     train_hex = h2o.uploadFile(h2oTest.locate("bigdata/laptop/mnist/train.csv.gz"))
     test_hex  = h2o.uploadFile(h2oTest.locate("bigdata/laptop/mnist/test.csv.gz" ))

     predictors = c(1:784)
     resp = 785
     nfeatures = 20 #number of features (smallest hidden layer)

     train_hex[,resp] <- as.factor(train_hex[,resp])
     test_hex[,resp] <- as.factor(test_hex[,resp])

     # split data into two parts (first part for unsupervised training, second part for supervised training)
     sid <- h2o.runif(train_hex, seed=0)
     # split <- h2o.splitFrame(train_hex, 0.5)


     # first part of the data, without labels for unsupervised learning (DL auto-encoder)
     train_unsupervised <- train_hex[sid>=0.5,]
     summary(train_unsupervised)

     # second part of the data, with labels for supervised learning (drf)
     train_supervised <- train_hex[sid<0.5,]

     # train autoencoder on train_unsupervised
     ae_model <- h2o.deeplearning(x=predictors,
                                  training_frame=train_unsupervised[-resp],
                                  activation="Tanh",
                                  autoencoder=T,
                                  hidden=c(nfeatures),
                                  epochs=1,
                                  reproducible=T,#slow - turn off for real problems
                                  seed=1234)

     # convert train_supervised with autoencoder model to lower-dimensional space
     train_supervised_features <- h2o.deepfeatures(ae_model, train_supervised[-resp], layer=1)

     expect_equal(ncol(train_supervised_features), nfeatures)

     myX = c(1:nfeatures)
     myY = nfeatures+1
     # Now train DRF on extracted feature space, first need to add response back
     train_supervised_features <- h2o.cbind(train_supervised_features, train_supervised[resp])
     drf_model <- h2o.randomForest(training_frame=train_supervised_features, x=myX, y=myY, ntrees=10, seed=1234, min_rows=10)

     # Now test the DRF model on the test set (first need to process into the reduced feature space)
     test_features <- h2o.deepfeatures(ae_model, test_hex[,-resp], layer=1)
     test_features <- h2o.cbind(test_features,test_hex[,resp])
     cm <- h2o.confusionMatrix(drf_model, test_features)
     print(cm)

     # compare to pyunit_autoencoderDeepLearning_large.py
     expect_equal(cm$Error[11], 0.0880, tolerance = 0.001, scale = 1) # absolute difference: scale = 1

     
}

h2oTest.doTest("Deep Learning AutoEncoder MNIST", check.deeplearning_autoencoder)
