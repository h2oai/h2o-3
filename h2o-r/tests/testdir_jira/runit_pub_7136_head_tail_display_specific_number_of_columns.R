setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.head_tail_colnum_frame <- function() {
    
  iris <- h2o.importFile(locate("smalldata/iris/iris_train.csv"))
  head(iris,2,3)
  head(iris,2)
  head(iris,2)
  head(iris,,3)
  head(iris,2,0)
  head(iris,0,0)

  tail(iris,2,3)
  tail(iris,2)
  tail(iris,,3)
  tail(iris,0,3)
  tail(iris,2,0)
  tail(iris,0,0)
    
}

doTest("Test frame add.", test.head_tail_colnum_frame)
