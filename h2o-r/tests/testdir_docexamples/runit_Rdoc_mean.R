setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdocmean.golden <- function(H2Oserver) {

prosPath <- system.file("extdata", "prostate.csv", package="h2o")
prostate.hex <- h2o.importFile(H2Oserver, path = prosPath, key = "prostate.hex")
mean(prostate.hex$AGE)

testEnd()
}

doTest("R Doc Mean", test.rdocmean.golden)