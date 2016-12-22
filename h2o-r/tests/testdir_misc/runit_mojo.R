setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.mojo <- function() {
	dir <- paste0(sandbox(),"/mojoresults")
	dir.create(dir)
	iris.hex <- h2o.uploadFile(locate("smalldata/iris/iris.csv"), "iris.hex")
	hh <- h2o.gbm(x=c(1,2,3,4),y=5,training_frame=iris.hex)
	h2o.download_mojo(hh,path=dir) #check mojo
	h2o.download_mojo(hh,get_genmodel_jar=TRUE,path=dir) #Check genmodel.jar

	#Get MOJO and check size is adequate (> 1 byte)
	mojo <- unzip(paste0(dir,"/",hh@model_id,".zip"), exdir = dir)
	expect_true(object.size(mojo) > 1)

	#Delete direcory on exit
	on.exit(unlink(dir,recursive=TRUE))
}
doTest("Test MOJO", test.mojo)