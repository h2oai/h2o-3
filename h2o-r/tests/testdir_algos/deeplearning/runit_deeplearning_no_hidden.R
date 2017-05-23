setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



check.deeplearning_no_hidden <- function() {
	iris.hex <- h2o.uploadFile(locate("smalldata/iris/iris.csv"), "iris.hex")
	hh <- h2o.deeplearning(x=c(1,2,3,4),y=5,hidden=NULL,training_frame=iris.hex)
	hh <- h2o.deeplearning(x=c(1,2,3,4),y=5,hidden=c(),training_frame=iris.hex)
	print(hh)
  
}

doTest("Deep Learning Test: Iris", check.deeplearning_no_hidden)
