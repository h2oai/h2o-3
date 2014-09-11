setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../findNSourceUtils.R')

test.rdocclusterstatus.golden <- function(H2Oserver) {
	

 h2o.clusterStatus(H2Oserver)

testEnd()
}

doTest("R Doc Cluster Status", test.rdocclusterstatus.golden)

