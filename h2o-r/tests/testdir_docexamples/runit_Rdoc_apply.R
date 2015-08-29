setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdocapply.golden <- function() {
irisPath <- system.file("extdata", "iris.csv", package="h2o")
iris.hex <- h2o.uploadFile(path = irisPath, destination_frame = "iris.hex")
summary(apply(iris.hex, 1, sum))

testEnd()
}

doTest("R Doc Apply", test.rdocapply.golden)

