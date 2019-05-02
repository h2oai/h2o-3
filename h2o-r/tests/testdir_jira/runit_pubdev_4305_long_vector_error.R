setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


######################################################################################
# PUBDEV-4305 Test data conversion using writeBin instead of rawToChar method
######################################################################################
pubdev_4305_test <-
function() {
    options("h2o.verbose"=TRUE)
    file <- locate("smalldata/iris/iris.csv")
    bigdata <- read.csv(file, header = FALSE)
    
    h2oframe <- as.h2o(bigdata)
    
    options("h2o.as.data.frame.max.in-memory.payload.size"=4000)
    bigdataframe <- as.data.frame(h2oframe)
    
    expect_true(all.equal(bigdata,bigdataframe))
}

doTest("PUBDEV-4305", pubdev_4305_test)
