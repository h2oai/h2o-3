setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdocclusterstatus.golden <- function() {
	

 h2o.clusterStatus()


}

doTest("R Doc Cluster Status", test.rdocclusterstatus.golden)

