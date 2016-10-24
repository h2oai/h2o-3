setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

#Starting H2O server from R ignores IP and port parameters
#This is a test to see if there is a proper fix in place

test_pubdev2801 <- function(){
  h2o.init(port = 54355)
  h2o.shutdown(prompt=FALSE)
}

doTest("PUBDEV-2801: Starting H2O server from R ignores IP and port parameters", test_pubdev2801)