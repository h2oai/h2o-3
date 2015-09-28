setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdocclusterinfo.golden <- function() {
	

h2o.clusterInfo()


}

doTest("R Doc Cluster Info", test.rdocclusterinfo.golden)

