setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.rdocclusterinfo.golden <- function() {
	

h2o.clusterInfo()


}

h2oTest.doTest("R Doc Cluster Info", test.rdocclusterinfo.golden)

