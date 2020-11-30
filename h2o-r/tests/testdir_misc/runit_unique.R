setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


test <- function() {
	iris <- as.h2o(iris)
	unique_iris <- h2o.unique(iris["Species"])
	numNAs <- 40
  s <- sample(nrow(iris),numNAs)
  iris[s,5] <- NA
  expect_that(sum(is.na(iris[5])), equals(numNAs))
  unique_iris_nas <- h2o.unique(iris["Species"], include_nas = TRUE)
  expect_equal(c(3,1), dim(unique_iris))
  expect_equal(c(4,1), dim(unique_iris_nas))
  
  # Including NAS has to be explicitly enabled, otherwise the result should be the same.
  unique_iris_no_nas <- h2o.unique(iris["Species"])
  expect_equal(dim(unique_iris), dim(unique_iris_no_nas))
  
}

doTest("Test NAs in unique() operation", test)

