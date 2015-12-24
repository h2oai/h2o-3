setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.rdocstr.golden <- function() {

prosPath <- system.file("extdata", "prostate.csv", package="h2o")
prostate.hex <- h2o.uploadFile( path = prosPath)
str(prostate.hex)

}

doTest("R Doc str", test.rdocstr.golden)


