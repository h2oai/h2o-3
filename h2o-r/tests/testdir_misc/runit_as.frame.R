##
# Testing number of rows in as.data.frame 
##

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../findNSourceUtils.R')

# setupRandomSeed(1994831827)

test <- function(conn) {
    # For interactive debugging.
    # conn = h2o.init()
    
    Log.info("Reading prostate into R")	
	x = read.csv("../../../smalldata/logreg/prostate.csv", header=T)
	Log.info("Parsing prostate into H2O")	
	hex = h2o.uploadFile(conn, locate("../../../smalldata/logreg/prostate.csv"), "hex")
	Nhex = as.data.frame(hex)
	
	Log.info("Expect that number of rows in as.data.frame is same as the original file")
    print(sprintf("nrow(Nhex): %d", nrow(Nhex)))
    print(sprintf("nrow(x): %d", nrow(x)))
	expect_that(nrow(Nhex), equals(nrow(x)))
      
    testEnd()
}

doTest("Test data frame", test)

