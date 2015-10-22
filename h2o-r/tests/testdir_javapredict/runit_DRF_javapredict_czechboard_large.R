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

test.drf.javapredict.czech <-
function() {
    #----------------------------------------------------------------------
    # Parameters for the test.
    #----------------------------------------------------------------------
    training_file <- test_file <- locate("smalldata/gbm_test/czechboard_300x300.csv")
    training_frame <- h2o.importFile(training_file)
    test_frame <- h2o.importFile(test_file)

    params                 <- list()
    params$ntrees          <- 100
    params$max_depth       <- 5
    params$min_rows        <- 10
    params$x               <- c("C1", "C2")
    params$y               <- "C3"
    params$training_frame  <- training_frame

    #----------------------------------------------------------------------
    # Run the test
    #----------------------------------------------------------------------
    doJavapredictTest("randomForest",normalizePath(paste0(getwd(),"/..")),test_file,test_frame,params)
}

doTest("RF test", test.drf.javapredict.czech)
