setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.anyFactor <- function(localH2O) {

 irisPath <- system.file("extdata", "iris_wheader.csv", package="h2o")
 iris.hex <- h2o.uploadFile(path = irisPath)
 h2o.anyFactor(iris.hex)

testEnd()
}

doTest("R Doc h2o.anyFactor", test.anyFactor)
