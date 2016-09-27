setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.rdocmean.golden <- function() {

prosPath <- locate("smalldata/extdata/prostate.csv")
prostate.hex <- h2o.uploadFile(path = prosPath, destination_frame = "prostate.hex")
h2o.median(prostate.hex$AGE)


}

doTest("R Doc Median", test.rdocmean.golden)