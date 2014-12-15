setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdocRF.golden <- function(H2Oserver) {
	
irisPath <- system.file("extdata", "iris.csv", package="h2o")
iris.hex <- h2o.importFile(H2Oserver, path = irisPath, key = "iris.hex")
h2o.randomForest(y = 5, x = c(2,3,4), training_frame = iris.hex, ntree = 50, depth = 100, type = "BigData")

testEnd()
}

doTest("R Doc RF", test.rdocRF.golden)

