setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.predict_json <- function() {
  iris.hex <- h2o.uploadFile(locate("smalldata/iris/iris.csv"), "iris.hex")
  hh <- h2o.gbm(x=c(1,2,3,4),y=5,training_frame=iris.hex)
  file <- h2o.download_mojo(hh) #check mojo
  res <- h2o.predict_json(file, '{"C1":1}')
  expect_equal(res$labelIndex, 1)
  expect_equal(res$label, "Iris-versicolor")
  expect_equal(res$labelIndex, 1)
  expect_equal(res$classProbabilities, c(0.02486732, 0.95027105, 0.02486163))
}
doTest("Test predict_json", test.predict_json)


