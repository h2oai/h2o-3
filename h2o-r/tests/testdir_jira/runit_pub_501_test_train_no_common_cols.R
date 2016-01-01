setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pub.501 <- function() {
  h2oTest.logInfo("Importing ecology_model.csv and covtype.20k.data...")
  tr <- h2o.importFile(normalizePath(h2oTest.locate("smalldata/gbm_test/ecology_model.csv")), destination_frame = "tr")
  train <- h2o.importFile(normalizePath(h2oTest.locate("smalldata/covtype/covtype.20k.data")), destination_frame = "train")
  expect_false(any(colnames(train) %in% colnames(tr)))
  
  myX <- setdiff(colnames(tr), c("Angaus", "Site"))
  myY <- "Angaus"
  h2oTest.logInfo(paste("Run GBM on ecology_model.csv with Y:", myY, "\tX:", paste(myX, collapse = ", ")))
  tru.gbms <- h2o.gbm(x = myX, y = myY, training_frame = tr, distribution = "gaussian", ntrees = 400, nbins = 20, min_rows = 1, max_depth = 4, learn_rate = 0.01, validation_frame = tr)
  
  h2oTest.logInfo("Predict on covtype.20k.data using GBM model")
    expect_error(predict(object = tru.gbms, newdata = train))
  
}

h2oTest.doTest("Test PUBDEV-501: No error when predicting on test data with all different cols from train", test.pub.501)
