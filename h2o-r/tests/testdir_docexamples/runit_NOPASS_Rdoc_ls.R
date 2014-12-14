setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdocls.golden <- function(H2Oserver) {
	

prosPath = system.file("extdata", "prostate.csv", package="h2o")
prostate.hex = h2o.importFile(H2Oserver, path = prosPath, key = "prostate.hex")
s = runif(nrow(prostate.hex))
prostate.train = prostate.hex[s <= 0.8,]
prostate.train = h2o.assign(prostate.train, "prostate.train")
h2o.ls(H2Oserver)

testEnd()
}

doTest("R Doc ls", test.rdocls.golden)

