setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



check.deeplearning_MNIST_cp <- function() {
  Log.info("Deep Learning MNIST Checkpointing)")
  
  TRAIN <- "bigdata/laptop/mnist/train.csv.gz"
  TEST <- "bigdata/laptop/mnist/test.csv.gz"
  
  train_hex <- h2o.importFile(locate(TRAIN))
  test_hex  <- h2o.importFile(locate(TEST ))
  
  # Turn response into a factor (we want classification)
  train_hex[,785] <- as.factor(train_hex[,785])
  test_hex [,785] <- as.factor(test_hex [,785])
  train_hex <- h2o.assign(train_hex, "train.hex")
  test_hex  <- h2o.assign(test_hex , "test.hex" )
  
  # 1) Train deep learning model

  ## Start training on the test frame, which has fewer non-constant columns than the training frame
  model <- h2o.deeplearning(x=c(1:784), y=785,
                               training_frame=test_hex,
                               hidden=c(50,50),
                               train_samples_per_iteration=1000,
                               epochs=1
                              )
  
  ## Continue training on the training frame, which has different (and more) non-constant columns
  model2 <- h2o.deeplearning(checkpoint = model@model_id,
                                x=c(1:784), y=785,
                                training_frame=train_hex,
                                hidden=c(50,50),
                                train_samples_per_iteration=1000,
                                epochs=2
                               )
  
  checkTrue(model2@model$scoring_history$epochs[length(model2@model$scoring_history$epochs)] >= 2, "checkpointing didn't run through 2 epochs total!")
}

doTest("Deep Learning MNIST", check.deeplearning_MNIST_cp)

