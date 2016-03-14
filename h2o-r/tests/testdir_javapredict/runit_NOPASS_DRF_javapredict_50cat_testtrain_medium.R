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

# PUBDEV-2055
test.drf.javapredict.50cat <-
function() {

    training_file <- locate("smalldata/gbm_test/50_cattest_train.csv")
    test_file <- locate("smalldata/gbm_test/50_cattest_test.csv")
    training_frame <- h2o.importFile(training_file)
    test_frame <- h2o.importFile(test_file)

    params                 <- list()
    params$ntree           <- 50
    params$max_depth       <- 5
    params$min_rows        <- 10
    params$x               <- c("x1", "x2")
    params$y               <- "y"
    params$training_frame  <- training_frame

    doJavapredictTest("randomForest",test_file,test_frame,params)
}

doTest("DRF pojo test", test.drf.javapredict.50cat)
