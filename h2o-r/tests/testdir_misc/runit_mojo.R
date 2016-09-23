setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.mojo <- function() {
	iris.hex <- h2o.uploadFile(locate("smalldata/iris/iris.csv"), "iris.hex")
	hh <- h2o.gbm(x=c(1,2,3,4),y=5,training_frame=iris.hex)
	h2o.download_mojo(hh) #check mojo
	h2o.download_mojo(hh,get_genmodel_jar=TRUE) #Check genmodel.jar
}
doTest("Test MOJO", test.mojo)