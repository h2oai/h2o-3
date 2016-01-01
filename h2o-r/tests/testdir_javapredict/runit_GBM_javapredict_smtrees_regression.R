setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
#----------------------------------------------------------------------
# Purpose:  This test exercises the GBM model downloaded as java code
#           for the iris data set.
#
# Notes:    Assumes unix environment.
#           curl, javac, java must be installed.
#           java must be at least 1.6.
#----------------------------------------------------------------------



test.gbm.javapredict.smtrees <-
function() {
    #----------------------------------------------------------------------
    # Parameters for the test.
    #----------------------------------------------------------------------
    training_file <- h2oTest.locate("smalldata/gbm_test/smtrees.csv")
    test_file <- h2oTest.locate("smalldata/gbm_test/smtrees.csv")
    training_frame <- h2o.importFile(training_file)
    test_frame <- h2o.importFile(test_file)

    params                 <- list()
    params$ntrees          <- 3
    params$max_depth       <- 1
    params$min_rows        <- 2
    params$learn_rate      <- 1
    params$x               <- c("girth","height")
    params$y               <- "vol"
    params$training_frame  <- training_frame

    #----------------------------------------------------------------------
    # Run the test
    #----------------------------------------------------------------------
    h2oTest.doJavapredictTest("gbm",test_file,test_frame,params)
}

h2oTest.doTest("GBM test", test.gbm.javapredict.smtrees)
