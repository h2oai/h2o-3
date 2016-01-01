setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.rdocuploadfile.golden <- function() {
	
prostate.hex <- h2o.uploadFile(path = h2oTest.locate("smalldata/extdata/prostate.csv"), destination_frame = "prostate.hex")
summary(prostate.hex)


}

h2oTest.doTest("R Doc upload file", test.rdocuploadfile.golden)

