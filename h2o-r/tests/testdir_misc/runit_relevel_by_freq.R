setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.relevel_by_freq <- function() {
  iris_hf <- as.h2o(iris)
  expect_equal(c("setosa", "versicolor", "virginica"), h2o.levels(iris_hf["Species"]))

  # all species levels has the same size
  print(h2o.group_by(data=iris_hf, by="Species", nrow(1)))

  # reorder using frequencies - nothing should be change
  iris_releveled_hf <- h2o.relevel_by_frequency(x = iris_hf)
  expect_equal(c("setosa", "versicolor", "virginica"), h2o.levels(iris_releveled_hf["Species"]))

  print(h2o.group_by(data=iris_releveled_hf, by="Species", nrow(1)))

  # move only the most frequent level - nothing should be change
  iris_top1_hf <- h2o.relevel_by_frequency(x = iris_hf, top_n = 1)
  expect_equal(c("setosa", "versicolor", "virginica"), h2o.levels(iris_top1_hf["Species"]))

  # weighted reorder - weights change order
  iris_releveled_hf <- h2o.relevel_by_frequency(x = iris_hf, weights_column="Sepal.Width")
  expect_equal(c("setosa", "virginica", "versicolor"), h2o.levels(iris_releveled_hf["Species"]))
}

doTest("Test h2o.relevel_by_freq", test.relevel_by_freq)

