setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


test.ifelse <- function() {
  Log.info("Importing iris dataset into H2O...")
  iris.hex <- as.h2o(iris, "iris.hex")
  Log.info("Find Setosa species using R' ifelse...")
  setosa <- ifelse(iris$Species == "setosa", "N", "Y")
  Log.info("Find Setosa species H2O's ifelse...")
  setosa.hex <- ifelse(iris.hex$Species == "setosa", "N", "Y")

  expect_equal(as.data.frame(setosa.hex), data.frame(C1 = setosa, stringsAsFactors = TRUE))
}

doTest("R and H2O ifelse Function", test.ifelse)

