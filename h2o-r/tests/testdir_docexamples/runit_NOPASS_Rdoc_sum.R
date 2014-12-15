setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdocsum.golden <- function(H2Oserver) {

ausPath <- system.file("extdata", "australia.csv", package="h2o")
australia.hex <- h2o.importFile(H2Oserver, path = ausPath, key = "australia.hex")
sum(australia.hex)
sum(c(400, 1234, -1250), TRUE, australia.hex[,1:4])

testEnd()
}

doTest("R Doc Sum", test.rdocsum.golden)

