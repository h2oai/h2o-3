setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.relevel_by_freq <- function() {
  iris_hf <- as.h2o(iris)
  expect_equal(c("setosa", "versicolor", "virginica"), h2o.levels(iris_hf["Species"]))

  # reorder using frequencies 
  iris_releveled_hf <- h2o.relevel_by_frequency(x = iris_hf)
  expect_equal(c("virginica", "versicolor", "setosa"), h2o.levels(iris_releveled_hf["Species"]))

  # weighted reorder
  iris_releveled_hf <- h2o.relevel_by_frequency(x = iris_hf, weights_column="Sepal.Width")
  expect_equal(c("setosa", "virginica", "versicolor"), h2o.levels(iris_releveled_hf["Species"]))
}

doTest("Test h2o.relevel_by_freq", test.relevel_by_freq)

