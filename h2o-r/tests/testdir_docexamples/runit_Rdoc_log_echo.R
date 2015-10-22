setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdoc_log_echo.golden <- function() {
	

 h2o.logAndEcho("Test log and echo method.")


}

doTest("R Doc Log and Echo", test.rdoc_log_echo.golden)

