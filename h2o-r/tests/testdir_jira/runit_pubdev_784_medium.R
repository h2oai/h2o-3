setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test <- function() {
  data <- h2o.uploadFile(h2oTest.locate("bigdata/laptop/usecases/cup98LRN_z.csv"))
  dim(data)
  split = h2o.splitFrame(data=data,ratios=.8)
  train = h2o.assign(split[[1]],key="train")
  test = h2o.assign(split[[2]],key="test")
  dim(train)
  dim(test)

  data <- as.h2o(iris)
  split = h2o.splitFrame(data=data,ratios=.8)
  train = h2o.assign(split[[1]],key="train")
  test = h2o.assign(split[[2]],key="test")
  dim(train)
  dim(test)

  
}

h2oTest.doTest("PUBDEV-784", test)
