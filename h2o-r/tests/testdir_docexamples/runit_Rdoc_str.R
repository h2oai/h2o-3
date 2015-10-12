setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdocstr.golden <- function() {

prosPath <- locate("smalldata/extdata/prostate.csv", package="h2o")
prostate.hex <- h2o.uploadFile(path = prosPath)
str(prostate.hex)


}

doTest("R Doc str", test.rdocstr.golden)


