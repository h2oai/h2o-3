##
# Parse airlines_all
##

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

# setupRandomSeed(1994831827)

test <- function() {
	hex = h2o.importFile("/home/0xdiag/datasets/airlines/2008.csv", "hex")
  print(hex)
      
    testEnd()
}

doTest("Parse 2008 airlines dataset from NAS", test)

