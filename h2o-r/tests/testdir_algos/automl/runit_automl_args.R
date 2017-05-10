setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

automl.args.test <- function() {
  
  # This test checks that the h2o.automl arguments work as expected
  #
  # NOTE: It's currently not functional because we can't set different project names
  
  # Import data 
  #fr <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))
  fr <- h2o.importFile("/Users/me/h2oai/github/build-h2o/smalldata/logreg/prostate.csv")
  
  # Set up train, validation and test sets
  y <- "CAPSULE"
  x <- setdiff(names(fr), c(y, "ID"))
  fr[,y] <- as.factor(fr[,y])
  ss <- h2o.splitFrame(fr, ratios = c(0.8, 0.1))
  train <- ss[[1]]
  valid <- ss[[2]]
  test <- ss[[3]]
  msecs <- 10 #max_runtime_secs

  
  print("Check arguments to H2OAutoML class")
  # TO DO (project name is not added yet)
  # TO DO: Should we add some tests with x in there?
  # TO DO: Add testing functions around these function calls below

  print("AutoML run with x not provided and train set only")
  aml1 <- h2o.automl(y = y, training_frame = train, 
                     max_runtime_secs = msecs)
  
  print("AutoML run with x not provided; with train and valid")
  aml2 <- h2o.automl(y = y, training_frame = train, validation_frame = valid, 
                     max_runtime_secs = msecs)
  
  print("AutoML run with x not provided; with train and test")
  aml3 <- h2o.automl(y = y, training_frame = train, test_frame = test,
                     max_runtime_secs = msecs)
  
  print("AutoML run with x not provided; with train, valid, and test")
  aml4 <- h2o.automl(y = y, training_frame = train, validation_frame = valid, test_frame = test, 
                     max_runtime_secs = msecs)
  
  print("AutoML run with x not provided and y as col idx; with train, valid, and test")
  aml5 <- h2o.automl(y = 2, training_frame = train, max_runtime_secs = msecs)
  
}

doTest("AutoML Arguments Test", automl.args.test)