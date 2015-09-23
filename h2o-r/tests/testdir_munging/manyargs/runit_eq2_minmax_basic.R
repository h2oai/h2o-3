##
# Test: min & max
# Description: Select a dataset, select some columns, compute their min and max
##

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

#setupRandomSeed(1689636624)

test.basic.minmax <- function() {
  Log.info("Uploading iris data...")
  hex <- h2o.importFile( locate("smalldata/iris/iris_wheader.csv"), "iris.hex")

  Log.info("Computing min & max of the first column of iris...")
  iris1_min <- min(hex[,1]); Log.info("Minimum:"); print(iris1_min)
  iris1_max <- max(hex[,1]); Log.info("Maximum:"); print(iris1_max)
  expect_that(iris1_min, equals(min(iris[,1])))
  expect_that(iris1_max, equals(max(iris[,1])))

  Log.info("Computing min & max of all numeric columns of iris...")
  irisall_min <- min(hex[,-5]); Log.info("Minimum:"); print(irisall_min)
  irisall_max <- max(hex[,-5]); Log.info("Maximum"); print(irisall_max)
  expect_that(irisall_min, equals(min(iris[,-5])))
  expect_that(irisall_max, equals(max(iris[,-5])))
  
  Log.info("Shuffle order of differently typed arguments to min and max")
  expect_that(min(hex[, 1], 0, 2.5), equals(min(hex[, 1],  0, 2.5)))
  expect_that(min(hex[,-5], 4,  -5), equals(min(hex[,-5], -5,   4)))
  expect_that(max(hex[, 1], 5,   3), equals(max(hex[, 1],  3,   5)))
  expect_that(max(hex[,-5], 10,  3), equals(max(hex[,-5], 10,  -3)))

  Log.info("min and max corretness")
  df <- data.frame(c(1,-0.1,0))
  expect_that(min(as.h2o( df)), equals(min(df)))
  expect_that(max(as.h2o( df)), equals(max(df)))
  
  testEnd()
}

doTest("EQ2 Tests: min and max", test.basic.minmax)

