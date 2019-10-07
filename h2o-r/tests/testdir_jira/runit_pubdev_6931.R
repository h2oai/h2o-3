setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.proxy <- function() {
  Sys.setenv(http_proxy="http://fake-proxy:8080")
  data <- h2o.importFile(path = locate('smalldata/testng/airlines_train.csv'))
  Sys.unsetenv("http_proxy")
}

doTest("Bypass proxy", test.proxy)
