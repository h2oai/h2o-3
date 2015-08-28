setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdoccolnames.golden <- function(H2Oserver) {


irisPath <- system.file("extdata", "iris.csv", package="h2o")
iris.hex <- h2o.uploadFile(path = irisPath, destination_frame = "iris.hex")
summary(iris.hex)
colnames(iris.hex)

testEnd()
}

doTest("R Doc Col Names", test.rdoccolnames.golden)
