setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdocls.golden <- function(localH2O) {
	

prosPath <- system.file("extdata", "prostate.csv", package="h2o")
prostate.hex <- h2o.importFile(localH2O, path = prosPath)
h2o.ls(localH2O)

testEnd()
}

doTest("R Doc ls", test.rdocls.golden)