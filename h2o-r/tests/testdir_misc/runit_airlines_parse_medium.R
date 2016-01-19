setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
##
# Parse airlines_all
##




# setupRandomSeed(1994831827)

test <- function() {
	hex = h2o.importFile(locate("bigdata/laptop/airlines_all.05p.csv"), "hex")
  print(hex)
      
    
}

doTest("Parse 2008 airlines dataset from NAS", test)

