#----------------------------------------------------------------------
# Purpose:  This test exercises the RF model downloaded as java code
#           for the dhisttest data set. It checks whether the generated
#           java correctly splits categorical predictors into non-
#           contiguous groups at each node.
#
# Notes:    Assumes unix environment.
#           curl, javac, java must be installed.
#           java must be at least 1.6.
#----------------------------------------------------------------------
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../h2o-runit.R")

test.drf.javapredict.smallcat <-
function() {
    #----------------------------------------------------------------------
    # Parameters for the test.
    #----------------------------------------------------------------------
    training_file <- test_file <- locate("smalldata/gbm_test/alphabet_cattest.csv")
    training_frame <- h2o.importFile(training_file)
    test_frame <- h2o.importFile(test_file)

    params                 <- list()
    params$ntrees          <- 100
    params$max_depth       <- 5
    params$min_rows        <- 10
    params$x               <- c("X")
    params$y               <- "y"
    params$training_frame  <- training_frame

    #----------------------------------------------------------------------
    # Run the test
    #----------------------------------------------------------------------
    doJavapredictTest("randomForest",test_file,test_frame,params)
}

doTest("RF test", test.drf.javapredict.smallcat)
