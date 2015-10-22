##
# Parse airlines_all
##

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

# setupRandomSeed(1994831827)

test <- function() {
	hex = h2o.importFile("/home/0xdiag/datasets/airlines/airlines_all.csv", "hex")
  print(hex)
      
    
}

doTest("Parse 2008 airlines dataset from NAS", test)

