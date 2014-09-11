setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../findNSourceUtils.R')

test.rdocstr.golden <- function(H2Oserver) {

irisPath = system.file("extdata", "iris.csv", package="h2o")
iris.hex = h2o.importFile(H2Oserver, path = irisPath, key = "iris.hex")
summary(iris.hex)

testEnd()
}

doTest("R Doc str", test.rdocstr.golden)


