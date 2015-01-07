setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdocsum.golden <- function(H2Oserver) {

ausPath <- system.file("extdata", "australia.csv", package="h2o")
australia.hex <- h2o.importFile(H2Oserver, path = ausPath, key = "australia.hex")
sum(australia.hex)
sum(australia.hex[,1:4], australia.hex[,5:8], na.rm=FALSE)

testEnd()
}

doTest("R Doc Sum", test.rdocsum.golden)

