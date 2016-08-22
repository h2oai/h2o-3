setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
####### 
#This tests the default column type when there are mixed numeric and categorical entries 
########

test <- function(h) {
  data <- h2o.importFile(path=locate("smalldata/jira/hexdev_595.csv"),header=T)
  expect_equal(is.factor(data),c(FALSE, FALSE,  TRUE,  TRUE))
  
}
doTest("Parser default column type test", test)