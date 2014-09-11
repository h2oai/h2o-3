setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../findNSourceUtils.R')

test.rdocclusterinfo.golden <- function(H2Oserver) {
	

h2o.clusterInfo(H2Oserver)

testEnd()
}

doTest("R Doc Cluster Info", test.rdocclusterinfo.golden)

