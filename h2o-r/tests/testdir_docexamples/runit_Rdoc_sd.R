setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.rdocsd.golden <- function() {

    prosPath <- locate("smalldata/extdata/prostate.csv")
    prostate.hex <- h2o.uploadFile(path = prosPath)
    sd(prostate.hex$AGE)


}

doTest("R Doc SD", test.rdocsd.golden)

