setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
##
# Check factor levels of numeric and enum columns
##
test <- function(conn) {
  Log.info("Upload iris dataset into H2O...")
  iris.hex = as.h2o(iris)
  
  Log.info("Find the factor levels h2o and R frame...")
  levels1 <- sort(unlist(h2o.levels(iris.hex$Species)))
  levels2 <- sort(levels(iris$Species))
  print("Factor levels for Species column for H2OH2OFrame...")
  print(levels1)
  print("Factor levels for Species column for dataframe...")
  print(levels2)
  if(all(levels1 == levels2)){
    Log.info("Factor levels matches for Species Column...")
  } else {
    stop("Factor levels do not match for Species Column...")
  }
  
  Log.info("Try printing the levels of a numeric column...")
  levels1 <- levels(iris$Sepal.Length)
  levels2 <- unlist(h2o.levels(iris.hex$Sepal.Length))
  print("Factor levels for Sepal.Length column for H2OH2OFrame...")
  print(levels1)
  print("Factor levels for Sepal.Length column for dataframe...")
  print(levels2)  
  if(!is.null(levels2)) stop("Numeric Column should not have any factor levels...")

  allLevels <- h2o.levels(iris.hex)
  expect_true(is.list(allLevels))
  expect_true(length(allLevels) == ncol(iris.hex))
  numLevels <- h2o.nlevels(iris.hex)
  expect_true(length(numLevels) == 5)
  
  oneLevel <- h2o.levels(iris.hex[,5])
  expect_true(!is.list(oneLevel))
  expect_true(length(oneLevel) == 3)
  numLevels <- h2o.nlevels(iris.hex[,5])
  expect_true(numLevels == 3)
  
  oneLevel <- h2o.levels(iris.hex,5)
  expect_true(!is.list(oneLevel))
  expect_true(length(oneLevel) == 3)
  
}

doTest("Print factor levels with h2o.levels:", test)

