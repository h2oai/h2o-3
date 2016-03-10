setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
#----------------------------------------------------------------------
# Purpose:  This test exercises the GLM model downloaded as java code.
#
# Notes:    Assumes unix environment.
#           curl, javac, java must be installed.
#           java must be at least 1.6.
#----------------------------------------------------------------------

test.glm.prostate <-
function() {

    training_file <- locate("smalldata/prostate/prostate.csv")
    test_file <- locate("smalldata/prostate/prostate.csv")
    training_frame <- h2o.importFile(training_file)
    test_frame <- h2o.importFile(test_file)

    params                 <- list()
    params$x               <- c("AGE","RACE","DPROS","DCAPS","PSA","VOL","GLEASON")
    params$y               <- "CAPSULE"
    params$training_frame  <- training_frame

    doJavapredictTest("glm",test_file,test_frame,params)
}

doTest("GLM pojo test", test.glm.prostate)
