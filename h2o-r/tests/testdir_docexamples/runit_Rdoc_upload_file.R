setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdocuploadfile.golden <- function(H2Oserver) {
	
prostate.hex = h2o.uploadFile(H2Oserver, path = system.file("extdata", "prostate.csv", package="h2o"), key = "prostate.hex")
summary(prostate.hex)

testEnd()
}

doTest("R Doc upload file", test.rdocuploadfile.golden)

