setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

.import_dataset <- function(as_keys=FALSE) {
  y <- "CAPSULE"

  print("uploading dataset")
  train <- h2o.importFile(locate("smalldata/testng/prostate_train.csv"))
  train[,y] <- as.factor(train[,y])
  train <- h2o.assign(train, "prostate_train")
  test <- h2o.importFile(locate("smalldata/testng/prostate_test.csv"))
  test[,y] <- as.factor(test[,y])
  ss <- h2o.splitFrame(test, destination_frames = c("prostate_valid", "prostate_test"), seed = 1)
  valid <- ss[[1]]
  test <- ss[[2]]

  x <- setdiff(names(train), y)
  return(if (as_keys) list(x=x, y=y, train=h2o.getId(train), valid=h2o.getId(valid), blend=h2o.getId(test))
          else list(x=x, y=y, train=train, valid=valid, blend=test))
}

stackedensemble.frames_as_keys.test <- function() {
  
  nfolds <- 0
  seed <- 1

  ds <- .import_dataset(as_keys=TRUE)
  
  # Train & Cross-validate a GBM
  gbm <- h2o.gbm(x = ds$x,
                  y = ds$y,
                  training_frame = ds$train,
                  validation_frame = ds$valid,
                  ntrees = 10,
                  nfolds = nfolds,
                  seed = seed)
  
  # Train & Cross-validate a RF
  rf <- h2o.randomForest(x = ds$x,
                          y = ds$y,
                          training_frame = ds$train,
                          validation_frame = ds$valid,
                          ntrees = 10,
                          nfolds = nfolds,
                          seed = seed)
  
  # Train a stacked ensemble using the GBM and RF above
  se <- h2o.stackedEnsemble(x = ds$x,
                            y = ds$y,
                            training_frame = gbm@parameters$training_frame,
                            validation_frame = gbm@parameters$validation_frame,
                            blending_frame = ds$blend,
                            base_models = list(gbm, rf))

  expect_gt(h2o.auc(se), 0)
}

stackedensemble.invalid_keys.test <- function() {

  ds <- .import_dataset()

  frames <- list(
    list(training_frame='dummy'),
    list(training_frame=ds$train, validation_frame='dummy'),
    list(training_frame=ds$train, blending_frame='dummy')
  )
  for (fr in frames) {
    tryCatch({
      h2o.stackedEnsemble(x=ds$x, y=ds$y,
                          training_frame=fr$training_frame,
                          validation_frame=fr$validation_frame,
                          blending_frame=fr$blending_frame,
                          base_models = list())
      stop("should have raised error due to wrong frame key")
    },
    error=function(err) {
      dummy = names(fr[match("dummy", fr)])
      expect_equal(conditionMessage(err), paste0("argument '",dummy,"' must be a valid H2OFrame or key"))
    })
  }

}

doSuite("StackedEnsemble frames validation Test", makeSuite(
  stackedensemble.frames_as_keys.test,
  stackedensemble.invalid_keys.test
))
