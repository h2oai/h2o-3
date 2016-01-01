setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.ifce<- function() {

  hex <- as.h2o(iris)
  zhex <- hex - hex
  # h2o.exec(zhex <- hex - hex)
  
  
}

h2oTest.doTest("test ifce", test.ifce)
