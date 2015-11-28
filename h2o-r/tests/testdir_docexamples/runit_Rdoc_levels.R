setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.levels.golden <- function() {


irisPath <- locate("smalldata/extdata/iris.csv")
iris.hex <- h2o.uploadFile(path = irisPath, destination_frame = "iris.hex")
levels(iris.hex[,5])



}

doTest("R Doc levels", test.levels.golden)
