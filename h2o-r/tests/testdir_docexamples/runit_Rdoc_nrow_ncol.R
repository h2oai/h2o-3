setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.rdoc_nrow_ncol.golden <- function() {

irisPath <- h2oTest.locate("smalldata/extdata/iris.csv")
iris.hex <- h2o.uploadFile(path = irisPath, destination_frame = "iris.hex")
nrow(iris.hex)
ncol(iris.hex)


}

h2oTest.doTest("R Doc nrow and ncol", test.rdoc_nrow_ncol.golden)
