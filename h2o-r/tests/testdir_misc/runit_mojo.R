setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.mojo <- function() {
	#Set up tmp directory to write MOJO
	dir <- dir.create(file.path(sandbox(),"mojos"))
	#Set up path as dir.create() just sets up the directory and returns a logical based on if if failed or not
	path <- file.path(sandbox(),"mojos")

	#Build GBM model
	iris.hex <- h2o.uploadFile(locate("smalldata/iris/iris.csv"), "iris.hex")
	hh <- h2o.gbm(x=c(1,2,3,4),y=5,training_frame=iris.hex)

	#Download MOJO
	print(sprintf("Dowloading MOJO..."))
	start_time <- Sys.time()
	mojo_file <- h2o.download_mojo(hh,path=path) #check mojo
	end_time <- Sys.time()
	print(sprintf("MOJO file is %s bytes", object.size(mojo_file)))
	#Just a check for MOJO size (PUBDEV 3819)
	expect(object.size(mojo_file) > 1, "MOJO file should be greater than 1 byte!")
	print(sprintf("Time taken to dowloand MOJO: %s",end_time - start_time))

	#Download genmodel
	h2o.download_mojo(hh,path=path,get_genmodel_jar=TRUE) #Check genmodel.jar

	#Delete tmp directory
	on.exit(unlink(path,recursive=TRUE))
}
doTest("Test MOJO", test.mojo)