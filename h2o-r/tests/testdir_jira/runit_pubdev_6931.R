setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.proxy <- function() {
  Sys.setenv(http_proxy="http://fake-proxy:8080")
  conn <- h2o.init()
  Sys.unsetenv("http_proxy")
}

doTest("Bypass proxy", test.proxy)
