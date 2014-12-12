setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdocapply.golden <- function(H2Oserver) {
irisPath = system.file("extdata", "iris.csv", package="h2o")
iris.hex = h2o.importFile(H2Oserver, path = irisPath, key = "iris.hex")
summary(apply(iris.hex, 1, sum))

testEnd()
}

doTest("R Doc Apply", test.rdocapply.golden)

