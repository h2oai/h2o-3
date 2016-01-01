setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



check.deeplearning_MNIST <- function() {
  h2oTest.logInfo("Deep Learning MNIST Classification)")
  
  TRAIN <- "bigdata/laptop/mnist/train.csv.gz"
  TEST <- "bigdata/laptop/mnist/test.csv.gz"
  
  # set to FALSE for stand-alone demo
  if (T) {
    train_hex <- h2o.uploadFile(h2oTest.locate(TRAIN))
    test_hex  <- h2o.uploadFile(h2oTest.locate(TEST ))
  } else {
    library(h2o)
    homedir <- paste0(path.expand("~"),"/h2o-dev/") #modify if needed
    train_hex <- h2o.importFile(path = paste0(homedir,TRAIN), header = F, sep = ',')
    test_hex  <- h2o.importFile(path = paste0(homedir,TEST ), header = F, sep = ',')
  }
  
  # Turn response into a factor (we want classification)
  train_hex[,785] <- as.factor(train_hex[,785])
  test_hex [,785] <- as.factor(test_hex [,785])
  train_hex <- h2o.assign(train_hex, "train.hex")
  test_hex  <- h2o.assign(test_hex , "test.hex" )
  
  # 1) Train deep learning model
  dl_model <- h2o.deeplearning(x=c(1:784), y=785,
                               training_frame=train_hex,
                               activation="RectifierWithDropout",
                               input_dropout_ratio=0.2,
                               classification_stop=-1,  # Turn off early stopping
                               l1=1e-5,
                               hidden=c(128,128,256), epochs=10     # For RUnits
                               #hidden=c(1024,1024,2048), epochs=8000 # For world-record ~0.83% test set error
                              )
  
  # 2) Compute test set error
  print(h2o.performance(dl_model, test_hex ))
  print(h2o.performance(dl_model, train_hex))
  
  
}

h2oTest.doTest("Deep Learning MNIST", check.deeplearning_MNIST)

