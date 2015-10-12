setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdoccolnames.golden <- function() {


irisPath <- locate("smalldata/extdata/iris.csv", package="h2o")
iris.hex <- h2o.uploadFile(path = irisPath, destination_frame = "iris.hex")
summary(iris.hex)
colnames(iris.hex)


}

doTest("R Doc Col Names", test.rdoccolnames.golden)
