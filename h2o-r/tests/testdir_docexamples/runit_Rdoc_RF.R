setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdocRF.golden <- function() {

irisPath <- locate("smalldata/extdata/iris.csv", package="h2o")
iris.hex <- h2o.uploadFile(path = irisPath, destination_frame = "iris.hex")
h2o.randomForest(y = 5, x = c(2,3,4), training_frame = iris.hex, ntrees = 50, max_depth = 100)


}

doTest("R Doc RF", test.rdocRF.golden)

