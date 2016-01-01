setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
##
# Test: min & max
# Description: Select a dataset, select some columns, compute their min and max
##




#setupRandomSeed(1689636624)

test.basic.minmax <- function() {
  h2oTest.logInfo("Uploading iris data...")
  hex <- h2o.importFile( h2oTest.locate("smalldata/iris/iris_wheader.csv"), "iris.hex")

  h2oTest.logInfo("Computing min & max of the first column of iris...")
  iris1_min <- min(hex[,1]); h2oTest.logInfo("Minimum:"); print(iris1_min)
  iris1_max <- max(hex[,1]); h2oTest.logInfo("Maximum:"); print(iris1_max)
  expect_that(iris1_min, equals(min(iris[,1])))
  expect_that(iris1_max, equals(max(iris[,1])))

  h2oTest.logInfo("Computing min & max of all numeric columns of iris...")
  irisall_min <- min(hex[,-5]); h2oTest.logInfo("Minimum:"); print(irisall_min)
  irisall_max <- max(hex[,-5]); h2oTest.logInfo("Maximum"); print(irisall_max)
  expect_that(irisall_min, equals(min(iris[,-5])))
  expect_that(irisall_max, equals(max(iris[,-5])))
  
  h2oTest.logInfo("Shuffle order of differently typed arguments to min and max")
  expect_that(min(hex[, 1], 0, 2.5), equals(min(hex[, 1],  0, 2.5)))
  expect_that(min(hex[,-5], 4,  -5), equals(min(hex[,-5], -5,   4)))
  expect_that(max(hex[, 1], 5,   3), equals(max(hex[, 1],  3,   5)))
  expect_that(max(hex[,-5], 10,  3), equals(max(hex[,-5], 10,  -3)))

  h2oTest.logInfo("min and max corretness")
  df <- data.frame(c(1,-0.1,0))
  expect_that(min(as.h2o( df)), equals(min(df)))
  expect_that(max(as.h2o( df)), equals(max(df)))
  
  
}

h2oTest.doTest("EQ2 Tests: min and max", test.basic.minmax)

