setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


#analogous to pyunit_empty_strings.py
test.empty.strings <- function() {
  d <- as.data.frame(list(e=rep("",2),c=rep("",2), f=rep("",2)))
  h <- as.h2o(d)
  
  expect_equal(sum(is.na(h)),0)
  expect_equal(sum(h==""),nrow(h) * ncol(h))
  
  
}

h2oTest.doTest("Testing Empty Strings", test.empty.strings)
