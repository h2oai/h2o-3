setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
##
# Testing as.logical
##




# setupRandomSeed(1994831827)

test <- function() {
  # For interactive debugging.
  # conn = h2o.init()
  for (x in list(c('true',"T","F","false"), c(0, 1.2, -4))) h2o_and_R_equal(as.logical(as.h2o(x)), as.logical(x))

}

doTest("Test as.logical", test)

