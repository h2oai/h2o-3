setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.rdoccolnames.golden <- function() {


irisPath <- h2oTest.locate("smalldata/extdata/iris.csv")
iris.hex <- h2o.uploadFile(path = irisPath, destination_frame = "iris.hex")
summary(iris.hex)
colnames(iris.hex)


}

h2oTest.doTest("R Doc Col Names", test.rdoccolnames.golden)
