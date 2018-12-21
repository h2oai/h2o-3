setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
######################################################################################
# PUBDEV-4305 Test long vector conversion
######################################################################################
pubdev.4305.test <-
function() {
    file <- locate("smalldata/wa_cannabis/raw/Dashboard_Usable_Sales_w_Weight_Daily.csv")
    data <- h2o.importFile(file, destination_frame = "pubdev4305.data")

    dataH2o = as.h2o(data)
    dim(dataH2o)
    dataDF = as.data.frame(dataH2o)
}

doTest("PUBDEV-4305", pubdev.4305.test)
