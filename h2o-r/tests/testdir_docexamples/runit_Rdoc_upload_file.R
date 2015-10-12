setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdocuploadfile.golden <- function() {
	
prostate.hex <- h2o.uploadFile(path = locate("smalldata/extdata/prostate.csv", package="h2o"), destination_frame = "prostate.hex")
summary(prostate.hex)


}

doTest("R Doc upload file", test.rdocuploadfile.golden)

