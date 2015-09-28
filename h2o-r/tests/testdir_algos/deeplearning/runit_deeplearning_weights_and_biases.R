setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

check.deeplearning_imbalanced <- function() {
  Log.info("Test checks if Deep Learning weights and biases are accessible from R")
  
  covtype <- h2o.uploadFile(locate("smalldata/covtype/covtype.20k.data"))
  covtype[,55] <- as.factor(covtype[,55])
  dlmodel<-h2o.deeplearning(x=c(1:54),y=55,hidden=c(17,191),epochs=1,
                            training_frame=covtype,balance_classes=F,
                            reproducible=T, seed=1234, export_weights_and_biases=T)
  #print(dlmodel)

  weights1 <- h2o.getFrame(h2o.weights(dlmodel,matrix_id=1)$name)
  weights2 <- h2o.getFrame(h2o.weights(dlmodel,matrix_id=2)$name)
  weights3 <- h2o.getFrame(h2o.weights(dlmodel,matrix_id=3)$name)

  biases1 <- h2o.getFrame( h2o.biases(dlmodel,vector_id=1)$name)
  biases2 <- h2o.getFrame( h2o.biases(dlmodel,vector_id=2)$name)
  biases3 <- h2o.getFrame( h2o.biases(dlmodel,vector_id=3)$name)

  checkTrue(ncol(weights1) == 52, "wrong dimensionality!")
  checkTrue(nrow(weights1) == 17, "wrong dimensionality!")

  checkTrue(ncol(weights2) == 17, "wrong dimensionality!")
  checkTrue(nrow(weights2) == 191, "wrong dimensionality!")

  checkTrue(ncol(weights3) == 191, "wrong dimensionality!")
  checkTrue(nrow(weights3) == 7, "wrong dimensionality!")

  checkTrue(ncol(biases1) == 1, "wrong dimensionality!")
  checkTrue(nrow(biases1) == 17, "wrong dimensionality!")

  checkTrue(ncol(biases2) == 1, "wrong dimensionality!")
  checkTrue(nrow(biases2) == 191, "wrong dimensionality!")

  checkTrue(ncol(biases3) == 1, "wrong dimensionality!")
  checkTrue(nrow(biases3) == 7, "wrong dimensionality!")

  
}

doTest("Deep Learning Weights/Biases Test", check.deeplearning_imbalanced)
