setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../findNSourceUtils.R')

test.rdoc_log_echo.golden <- function(H2Oserver) {
	

 h2o.logAndEcho(H2Oserver, "Test log and echo method.")

testEnd()
}

doTest("R Doc Log and Echo", test.rdoc_log_echo.golden)

