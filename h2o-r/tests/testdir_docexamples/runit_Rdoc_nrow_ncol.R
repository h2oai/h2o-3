setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdoc_nrow_ncol.golden <- function() {

irisPath <- system.file("extdata", "iris.csv", package="h2o")
iris.hex <- h2o.uploadFile(path = irisPath, destination_frame = "iris.hex")
nrow(iris.hex)
ncol(iris.hex)


}

doTest("R Doc nrow and ncol", test.rdoc_nrow_ncol.golden)
