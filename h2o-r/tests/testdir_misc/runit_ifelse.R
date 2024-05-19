setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


test.ifelse <- function() {
  Log.info("Importing iris dataset into H2O...")
  iris.hex <- as.h2o(iris, "iris.hex")
  Log.info("Find Setosa species using R' ifelse...")
  setosa <- ifelse(iris$Species == "setosa", "N", "Y")
  Log.info("Find Setosa species H2O's ifelse...")
  setosa.hex <- ifelse(iris.hex$Species == "setosa", "N", "Y")

  expect_equal(as.data.frame(setosa.hex), data.frame(C1 = setosa))

  Log.info("Test case for all TRUE condition with categorical column")
  df <- as.h2o(data.frame(a = c("a", "a", "a"), stringsAsFactors = TRUE))
  result <- h2o.ifelse(df$a == "a", df$a, "d")
  expected_result <- data.frame(C1 = c("a", "a", "a"), stringsAsFactors = TRUE)
  expect_equal(as.data.frame(result), expected_result)
  
  Log.info("Test case for all FALSE condition with categorical column")
  df <- as.h2o(data.frame(a = c("a", "b", "c"), stringsAsFactors = TRUE))
  result <- h2o.ifelse(df$a == "d", "d", df$a)
  expected_result <- data.frame(C1 = c("a", "b", "c"), stringsAsFactors = TRUE)
  expect_equal(as.data.frame(result), expected_result)
}

doTest("R and H2O ifelse Function", test.ifelse)

