setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.rdocclusterstatus.golden <- function() {
	

 h2o.clusterStatus()


}

h2oTest.doTest("R Doc Cluster Status", test.rdocclusterstatus.golden)

