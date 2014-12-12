setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdocextremes.golden <- function(H2Oserver) {
	

ausPath = system.file("extdata", "australia.csv", package="h2o")
australia.hex = h2o.importFile(H2Oserver, path = ausPath, key = "australia.hex")
min(australia.hex)
min(c(-1, 0.5, 0.2), FALSE, australia.hex[,1:4])


testEnd()
}

doTest("R Doc Extremes", test.rdocextremes.golden)

