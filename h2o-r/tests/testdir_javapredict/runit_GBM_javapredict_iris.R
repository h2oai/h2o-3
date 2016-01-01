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



test.gbm.javapredict.iris <-
function() {
    #----------------------------------------------------------------------
    # Parameters for the test.
    #----------------------------------------------------------------------
    training_file <- h2oTest.locate("smalldata/iris/iris_train.csv")
    test_file <- h2oTest.locate("smalldata/iris/iris_test.csv")
    training_frame <- h2o.importFile(training_file)
    test_frame <- h2o.importFile(test_file)

    params                 <- list()
    params$ntrees          <- 100
    params$max_depth       <- 5
    params$min_rows        <- 10
    params$learn_rate      <- 0.1
    params$x               <- c("sepal_len","sepal_wid","petal_len","petal_wid");
    params$y               <- "species"
    params$training_frame  <- training_frame

    #----------------------------------------------------------------------
    # Run the test
    #----------------------------------------------------------------------
    h2oTest.doJavapredictTest("gbm",test_file,test_frame,params)
}

h2oTest.doTest("GBM test", test.gbm.javapredict.iris)
