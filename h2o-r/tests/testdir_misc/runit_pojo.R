setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
#----------------------------------------------------------------------
# Purpose:  This test is a sanity check for a pojo download
#----------------------------------------------------------------------
test.pojo <- function() {
	iris.hex <- h2o.uploadFile(locate("smalldata/iris/iris.csv"), "iris.hex")
	hh <- h2o.gbm(x=c(1,2,3,4),y=5,training_frame=iris.hex)
	h2o.download_pojo(hh) #check pojo
	h2o.download_pojo(hh,get_jar=TRUE) #Check genmodel.jar
}
doTest("Test POJO", test.pojo)