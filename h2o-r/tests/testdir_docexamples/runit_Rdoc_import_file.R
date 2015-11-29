setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.rdocstr.golden <- function() {

irisPath <- locate("smalldata/extdata/iris.csv")
iris.hex <- h2o.uploadFile(path = irisPath, destination_frame = "iris.hex")
summary(iris.hex)


}

doTest("R Doc str", test.rdocstr.golden)


