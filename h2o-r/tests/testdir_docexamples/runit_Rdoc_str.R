setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.rdocstr.golden <- function() {

prosPath <- h2oTest.locate("smalldata/extdata/prostate.csv")
prostate.hex <- h2o.uploadFile(path = prosPath)
str(prostate.hex)


}

h2oTest.doTest("R Doc str", test.rdocstr.golden)


