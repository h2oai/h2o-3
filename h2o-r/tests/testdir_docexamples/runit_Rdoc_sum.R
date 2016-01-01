setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.rdocsum.golden <- function() {

ausPath <- h2oTest.locate("smalldata/extdata/australia.csv")
australia.hex <- h2o.uploadFile(path = ausPath, destination_frame = "australia.hex")
sum(australia.hex)
sum(australia.hex[,1:4], australia.hex[,5:8], na.rm=FALSE)


}

h2oTest.doTest("R Doc Sum", test.rdocsum.golden)

