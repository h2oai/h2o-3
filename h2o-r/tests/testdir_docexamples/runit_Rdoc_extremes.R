setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.rdocextremes.golden <- function() {


ausPath = h2oTest.locate("smalldata/extdata/australia.csv")
australia.hex = h2o.uploadFile(path = ausPath, destination_frame = "australia.hex")
min(australia.hex)
min(australia.hex[,1:4], australia.hex[,5:8], na.rm=FALSE)



}

h2oTest.doTest("R Doc Extremes", test.rdocextremes.golden)

