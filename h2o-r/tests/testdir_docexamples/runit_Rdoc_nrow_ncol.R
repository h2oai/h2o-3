setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdoc_nrow_ncol.golden <- function(H2Oserver) {

irisPath <- system.file("extdata", "iris.csv", package="h2o")
iris.hex <- h2o.importFile(H2Oserver, path = irisPath, key = "iris.hex")
nrow(iris.hex)
ncol(iris.hex)

testEnd()
}

doTest("R Doc nrow and ncol", test.rdoc_nrow_ncol.golden)