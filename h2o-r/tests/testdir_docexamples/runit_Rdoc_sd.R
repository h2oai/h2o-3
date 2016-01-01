setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.rdocsd.golden <- function() {

    prosPath <- h2oTest.locate("smalldata/extdata/prostate.csv")
    prostate.hex <- h2o.uploadFile(path = prosPath)
    sd(prostate.hex$AGE)


}

h2oTest.doTest("R Doc SD", test.rdocsd.golden)

