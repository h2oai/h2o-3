setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pubdev.2041 <- function(conn) {
  iris = h2o.importFile(h2oTest.locate("smalldata/iris/iris.csv"))

  s = h2o.runif(iris,seed=12345)
  train1 = iris[s >= 0.5,]
  train2 = iris[s <  0.5,]

  m1 = h2o.deeplearning(x=1:4, y=5, training_frame=train1, epochs=100)

  # update m1 with new training data
  m2 = h2o.deeplearning(x=1:4, y=5, training_frame=train2, epochs=200, checkpoint=m1@model_id)

  
}

h2oTest.doTest("PUBDEV-2041", test.pubdev.2041)
