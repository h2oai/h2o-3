setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdoc_if_else.golden <- function() {

ausPath <- locate("smalldata/extdata/australia.csv", package="h2o")
australia.hex <- h2o.uploadFile(path = ausPath)
australia.hex[,9] <- ifelse(australia.hex[,3] < 279.9, 1, 0)


}

doTest("R Doc If Else", test.rdoc_if_else.golden)

