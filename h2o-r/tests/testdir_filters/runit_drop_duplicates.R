setwd(normalizePath(dirname(
  R.utils::commandArgs(asValues = TRUE)$"f"
)))
source("../../scripts/h2o-r-test-setup.R")

test.drop_duplicates <- function() {
  data <- as.h2o(iris)
  
  # Iris - keep first
  deduplicated_data <- h2o.drop_duplicates(data, c("Species", "Sepal.Length"), keep = "first")
  print(deduplicated_data)
  expect_equal(dim(unique(iris[,c("Species", "Sepal.Length")]))[1], h2o.nrow(deduplicated_data))
  
  # Iris - keep last
  deduplicated_data <- h2o.drop_duplicates(data, c("Species", "Sepal.Length"), keep = "last")
  print(deduplicated_data)
  expect_equal(dim(unique(iris[,c("Species", "Sepal.Length")]))[1], h2o.nrow(deduplicated_data))
  
  # Iris - numerical indices
  deduplicated_data <- h2o.drop_duplicates(data, c(0,4))
  print(deduplicated_data)
  expect_equal(dim(unique(iris[,c(1,5)]))[1], h2o.nrow(deduplicated_data))
  
  # Iris - numerical indices RANGE
  deduplicated_data <- h2o.drop_duplicates(data, c(0:4))
  print(deduplicated_data)
  expect_equal(dim(unique(iris[,c(1:5)]))[1], h2o.nrow(deduplicated_data))
  
  # Iris - single column, keep left to default
  data <- as.h2o(iris)
  deduplicated_data <- h2o.drop_duplicates(data, c("Species"))
  print(deduplicated_data)
}

doTest("Test drop duplicates", test.drop_duplicates)
