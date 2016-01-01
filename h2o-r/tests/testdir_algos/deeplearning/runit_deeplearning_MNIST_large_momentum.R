setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



check.deeplearning_MNIST <- function() {
  h2oTest.logInfo("Deep Learning MNIST Classification)")
  
  TRAIN <- "bigdata/laptop/mnist/train.csv.gz"
  TEST <- "bigdata/laptop/mnist/test.csv.gz"
  
  # set to FALSE for stand-alone demo
  if (T) {
    train_hex <- h2o.uploadFile(h2oTest.locate(TRAIN))
    test_hex <- h2o.uploadFile(h2oTest.locate(TEST))
  } else {
    library(h2o)
    conn <- h2o.init(nthreads=-1)
    homedir <- paste0(path.expand("~"),"/h2o-dev/") #modify if needed
    train_hex <- h2o.importFile(path = paste0(homedir,TRAIN), header = F, sep = ',')
    test_hex <- h2o.importFile(path = paste0(homedir,TEST), header = F, sep = ',')
  }
  
  # Turn response into a factor (we want classification)
  train_hex[,785] <- as.factor(train_hex[,785])
  test_hex[,785] <- as.factor(test_hex[,785])
  train_hex <- h2o.assign(train_hex, "train.hex")
  test_hex <- h2o.assign(test_hex, "test.hex")
  
  # 1) Train deep learning model
  dl_model <- h2o.deeplearning(x=c(1:784), y=785,
                               training_frame=train_hex,
                               activation="RectifierWithDropout",
                               adaptive_rate=F,
                               rate=0.01,
                               rate_decay=0.9,
                               rate_annealing=1e-6,
                               momentum_start=0.95, 
                               momentum_ramp=1e5, 
                               momentum_stable=0.99,
                               nesterov_accelerated_gradient=F,
                               input_dropout_ratio=0.2,
                               train_samples_per_iteration=20000,
                               classification_stop=-1,  # Turn off early stopping
                               l1=1e-5,
                               hidden=c(128,128,256), epochs=10     # For RUnits
                              )
  
  # 2) Compute test set error
  print(h2o.performance(dl_model, test_hex))
  checkTrue(h2o.mse(dl_model) <= 0.10, "test set MSE is worse than 0.10!")
  
  
}

h2oTest.doTest("Deep Learning MNIST", check.deeplearning_MNIST)

