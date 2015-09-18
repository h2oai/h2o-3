setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdocuploadfile.golden <- function() {
	
prostate.hex <- h2o.uploadFile(path = system.file("extdata", "prostate.csv", package="h2o"), destination_frame = "prostate.hex")
summary(prostate.hex)

testEnd()
}

doTest("R Doc upload file", test.rdocuploadfile.golden)

