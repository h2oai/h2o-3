setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.rdoc_log_echo.golden <- function() {
	

 h2o.logAndEcho("Test log and echo method.")


}

h2oTest.doTest("R Doc Log and Echo", test.rdoc_log_echo.golden)

