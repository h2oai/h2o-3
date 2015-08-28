setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdocclusterinfo.golden <- function(H2Oserver) {
	

h2o.clusterInfo()

testEnd()
}

doTest("R Doc Cluster Info", test.rdocclusterinfo.golden)

