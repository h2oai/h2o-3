setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
##
# Testing number of rows in as.data.frame 
##




# setupRandomSeed(1994831827)

test <- function() {
    # For interactive debugging.
    # conn = h2o.init()
    
    h2oTest.logInfo("Reading prostate into R")	
	x = read.csv(h2oTest.locate("smalldata/logreg/prostate.csv"), header=T)
	h2oTest.logInfo("Parsing prostate into H2O")	
	hex = h2o.importFile(h2oTest.locate("smalldata/logreg/prostate.csv"), "hex")
	Nhex = as.data.frame(hex)
	
	h2oTest.logInfo("Expect that number of rows in as.data.frame is same as the original file")
    print(sprintf("nrow(Nhex): %d", nrow(Nhex)))
    print(sprintf("nrow(x): %d", nrow(x)))
	expect_that(nrow(Nhex), equals(nrow(x)))
      
    
}

h2oTest.doTest("Test data frame", test)

