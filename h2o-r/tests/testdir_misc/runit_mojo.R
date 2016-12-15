setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.mojo <- function() {
	dir <- sandbox() #Set up tmp directory to write MOJO
	iris.hex <- h2o.uploadFile(locate("smalldata/iris/iris.csv"), "iris.hex")
	hh <- h2o.gbm(x=c(1,2,3,4),y=5,training_frame=iris.hex)
	h2o.download_mojo(hh,path=dir) #check mojo
	h2o.download_mojo(hh,path=dir,get_genmodel_jar=TRUE) #Check genmodel.jar

	#Delete tmp directory
	on.exit(unlink(dir,recursive=TRUE))
}
doTest("Test MOJO", test.mojo)