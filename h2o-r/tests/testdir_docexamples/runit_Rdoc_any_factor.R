setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../findNSourceUtils.R')

test.rdocanyfactor.golden <- function(H2Oserver) {
	

irisPath = system.file("extdata", "iris_wheader.csv", package="h2o")
iris.hex = h2o.importFile(H2Oserver, path = irisPath)
h2o.anyFactor(iris.hex)

testEnd()
}

doTest("R Doc Any Factor", test.rdocanyfactor.golden)

