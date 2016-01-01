setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.rdocRF.golden <- function() {

irisPath <- h2oTest.locate("smalldata/extdata/iris.csv")
iris.hex <- h2o.uploadFile(path = irisPath, destination_frame = "iris.hex")
h2o.randomForest(y = 5, x = c(2,3,4), training_frame = iris.hex, ntrees = 50, max_depth = 100)


}

h2oTest.doTest("R Doc RF", test.rdocRF.golden)

