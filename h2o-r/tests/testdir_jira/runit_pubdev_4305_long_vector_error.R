setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


######################################################################################
# PUBDEV-4305 Test long vector conversion
######################################################################################
pubdev_4305_test <-
function() {
    file <- locate("smalldata/wa_cannabis/raw/Dashboard_Usable_Sales_w_Weight_Daily.csv")
    bigdata <- read.csv(file, header = FALSE)
    h2oframe = as.h2o(bigdata)
    bigdataframe = as.data.frame(h2oframe)
    
    expect_true(all.equal(bigdata,bigdataframe))
}

doTest("PUBDEV-4305", pubdev_4305_test)
