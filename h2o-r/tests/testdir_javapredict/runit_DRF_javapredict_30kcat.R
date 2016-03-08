setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
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

test.drf.javapredict.30kcat <-
function() {

    training_file <- locate("smalldata/gbm_test/30k_cattest.csv")
    test_file <- locate("smalldata/gbm_test/30k_cattest.csv")
    training_frame <- h2o.importFile(training_file)
    test_frame <- h2o.importFile(test_file)

    params                 <- list()
    params$ntree           <- 50
    params$max_depth       <- 5
    params$min_rows        <- 10
    params$x               <- c("C1", "C2")
    params$y               <- "C3"
    params$training_frame  <- training_frame

    doJavapredictTest("randomForest",test_file,test_frame,params)
}

doTest("DRF pojo test", test.drf.javapredict.30kcat)
