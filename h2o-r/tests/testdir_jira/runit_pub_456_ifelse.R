setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pub.456 <- function() {
  a <- as.h2o(iris)

  a[,1] <- ifelse(a[,1] == 0, 54321, 54321)
   
  
}

h2oTest.doTest("Test pub 456", test.pub.456)
