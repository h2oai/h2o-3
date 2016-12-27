setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.pojo <- function() {
	#Set up tmp directory to write POJO
	dir <- dir.create(file.path(sandbox(),"pojos"))
	#Set up path as dir.create() just sets up the directory and returns a logical based on if if failed or not
	path <- file.path(sandbox(),"pojos")

	#Build GBM model
	iris.hex <- h2o.uploadFile(locate("smalldata/iris/iris.csv"), "iris.hex")
	hh <- h2o.gbm(x=c(1,2,3,4),y=5,training_frame=iris.hex)

	#Download POJO
	print(sprintf("Dowloading POJO..."))
	start_time <- Sys.time()
	pojo_file <- h2o.download_pojo(hh,path=path) #check pojo
	end_time <- Sys.time()
	print(sprintf("POJO file is %s bytes", object.size(pojo_file)))
	print(sprintf("Time taken to dowloand POJO: %s",end_time - start_time))

	#Download genmodel
	h2o.download_pojo(hh,path=path,get_jar=TRUE) #Check genmodel.jar

	#Delete tmp directory
	on.exit(unlink(path,recursive=TRUE))
}
doTest("Test POJO", test.pojo)