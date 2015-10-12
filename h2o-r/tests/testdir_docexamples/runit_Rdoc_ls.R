setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdocls.golden <- function() {


prosPath <- locate("smalldata/extdata/prostate.csv", package="h2o")
prostate.hex <- h2o.uploadFile(path = prosPath)
h2o.ls()


}

doTest("R Doc ls", test.rdocls.golden)
