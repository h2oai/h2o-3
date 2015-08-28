setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdocclusterstatus.golden <- function(H2Oserver) {
	

 h2o.clusterStatus()

testEnd()
}

doTest("R Doc Cluster Status", test.rdocclusterstatus.golden)

