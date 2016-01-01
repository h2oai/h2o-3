setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



check.deeplearning_imbalanced <- function() {
  h2oTest.logInfo("Test checks if Deep Learning weights and biases are accessible from R")
  
  census <- h2o.uploadFile(h2oTest.locate("smalldata/chicago/chicagoCensus.csv"))
  census[,1] <- as.factor(census[,1])
  dlmodel<-h2o.deeplearning(x=c(1:3),y=4,hidden=c(17,191),epochs=1,
                            training_frame=census,balance_classes=F,
                            reproducible=T, seed=1234, export_weights_and_biases=T)
  #print(dlmodel)

  weights1 <- h2o.weights(dlmodel,matrix_id=1)
  print(head(weights1))
  weights2 <- h2o.weights(dlmodel,matrix_id=2)
  weights3 <- h2o.weights(dlmodel,matrix_id=3)

  biases1 <- h2o.biases(dlmodel,vector_id=1)
  biases2 <- h2o.biases(dlmodel,vector_id=2)
  biases3 <- h2o.biases(dlmodel,vector_id=3)

  checkTrue(ncol(weights1) == 79, "wrong dimensionality!")
  checkTrue(nrow(weights1) == 17, "wrong dimensionality!")

  checkTrue(ncol(weights2) == 17, "wrong dimensionality!")
  checkTrue(nrow(weights2) == 191, "wrong dimensionality!")

  checkTrue(ncol(weights3) == 191, "wrong dimensionality!")
  checkTrue(nrow(weights3) == 1, "wrong dimensionality!")

  checkTrue(ncol(biases1) == 1, "wrong dimensionality!")
  checkTrue(nrow(biases1) == 17, "wrong dimensionality!")

  checkTrue(ncol(biases2) == 1, "wrong dimensionality!")
  checkTrue(nrow(biases2) == 191, "wrong dimensionality!")

  checkTrue(ncol(biases3) == 1, "wrong dimensionality!")
  checkTrue(nrow(biases3) == 1, "wrong dimensionality!")

  
}

h2oTest.doTest("Deep Learning Weights/Biases Test", check.deeplearning_imbalanced)
