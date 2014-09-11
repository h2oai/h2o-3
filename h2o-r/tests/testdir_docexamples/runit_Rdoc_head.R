setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../findNSourceUtils.R')

test.head.golden <- function(H2Oserver) {
	
ausPath = system.file("extdata", "australia.csv", package="h2o")
australia.hex = h2o.importFile(H2Oserver, path = ausPath)
head(australia.hex, 10)


testEnd()
}

doTest("R Doc head", test.head.golden)