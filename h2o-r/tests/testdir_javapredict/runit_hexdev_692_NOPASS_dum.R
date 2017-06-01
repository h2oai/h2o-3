setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

#----------------------------------------------------------------------
# Used to test out customer dataset to make sure h2o model predict and pojo predict generate
# the same answers.  However, customer dataset is not to be made public and hence this test
# is a NOPASS.
#----------------------------------------------------------------------
test <-
  function() {
    #----------------------------------------------------------------------
    # Parameters for the test.
    #----------------------------------------------------------------------
    
    # Story:
    # The objective of the test is to verify java code generation
    # for big models containing huge amount of trees.
    # This case verify multi-classifiers.
    training_file <- test_file <- locate("smalldata/Training_Data.csv")
    #training_file <- test_file <- locate("smalldata/dd.csv")
    
    training_frame <- h2o.importFile(training_file)
    test_frame <- h2o.importFile(test_file)
    browser()
    params                 <- list()
    params$ntrees          <- 100
    params$max_depth       <- 7
    params$x               <- 1:14
    params$y               <- "Label"
    params$training_frame  <- training_frame
    params$seed            <- 42
    
    doJavapredictTest("gbm",test_file,test_frame,params)
  }

doTest("gbm test", test)