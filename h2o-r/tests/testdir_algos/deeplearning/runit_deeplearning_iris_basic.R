setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



check.deeplearning_basic <- function() {
	iris.hex <- h2o.uploadFile(h2oTest.locate("smalldata/iris/iris.csv"), "iris.hex")
	hh <- h2o.deeplearning(x=c(1,2,3,4),y=5,training_frame=iris.hex)
	print(hh)
  
}

h2oTest.doTest("Deep Learning Test: Iris", check.deeplearning_basic)

