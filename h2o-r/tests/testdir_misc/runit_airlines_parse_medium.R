setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
##
# Parse airlines_all
##




# setupRandomSeed(1994831827)

test <- function() {
	hex = h2o.importFile(h2oTest.locate("bigdata/server/airlines_all.csv"), "hex")
	print(hex)
      
    
}

h2oTest.doTest("Parse 2008 airlines dataset from NAS", test)

