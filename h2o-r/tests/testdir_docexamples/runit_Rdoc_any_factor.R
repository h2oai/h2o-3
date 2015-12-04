setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.anyFactor <- function() {

 irisPath <- system.file("extdata", "iris_wheader.csv", package="h2o")
 iris.hex <- h2o.uploadFile(path = irisPath)
 h2o.anyFactor(iris.hex)

}

doTest("R Doc h2o.anyFactor", test.anyFactor)
