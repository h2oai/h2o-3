setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../findNSourceUtils.R')

test.rdocsd.golden <- function(H2Oserver) {

irisPath = system.file("extdata", "iris.csv", package="h2o")
iris.hex = h2o.importFile(H2Oserver, path = irisPath, key = "iris.hex")
sd(iris.hex[,4])

testEnd()
}

doTest("R Doc SD", test.rdocsd.golden)

