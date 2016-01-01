setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.rdoc_if_else.golden <- function() {

ausPath <- h2oTest.locate("smalldata/extdata/australia.csv")
australia.hex <- h2o.uploadFile(path = ausPath)
australia.hex[,9] <- ifelse(australia.hex[,3] < 279.9, 1, 0)


}

h2oTest.doTest("R Doc If Else", test.rdoc_if_else.golden)

