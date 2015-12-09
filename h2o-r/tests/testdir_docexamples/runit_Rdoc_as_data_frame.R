setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.rdocasdataframe.golden <- function() {

    prosPath <- locate("smalldata/extdata/prostate.csv")
    prostate.hex <- h2o.uploadFile(path = prosPath)
    as.data.frame.H2OFrame(prostate.hex)

    
}

doTest("R Doc as.data.frame", test.rdocasdataframe.golden)

