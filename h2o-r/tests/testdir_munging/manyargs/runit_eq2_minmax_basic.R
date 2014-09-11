##
# Test: min & max
# Description: Select a dataset, select some columns, compute their min and max
##

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../findNSourceUtils.R')

#setupRandomSeed(1689636624)

test.basic.minmax <- function(conn) {
  Log.info("Uploading iris data...")
  hex <- h2o.uploadFile(conn, locate("smalldata/iris/iris_wheader.csv"), "iris.hex")
  data(iris)

  Log.info("Computing min & max of the first column of iris...")
  iris1_min <- min(hex[,1]); Log.info(paste("Minimum:", iris1_min))
  iris1_max <- max(hex[,1]); Log.info(paste("Maximum:", iris1_max))
  expect_that(iris1_min, equals(min(iris[,1])))
  expect_that(iris1_max, equals(max(iris[,1])))

  Log.info("Computing min & max of all numeric columns of iris...")
  irisall_min <- min(hex[,-5]); Log.info(paste("Minimum:", irisall_min))
  irisall_max <- max(hex[,-5]); Log.info(paste("Maximum", irisall_max))
  expect_that(irisall_min, equals(min(iris[,-5])))
  expect_that(irisall_max, equals(max(iris[,-5])))
  
  Log.info("Shuffle order of differently typed arguments to min and max")
  expect_that(min(hex[,1], 0, 2.5), equals(min(0, hex[,1], 2.5)))
  expect_that(min(hex[,-5], 4, -5), equals(min(-5, 4, hex[,-5])))
  expect_that(max(hex[,1], 5, 3), equals(max(3, 5, hex[,1])))
  expect_that(max(hex[,-5], 1:10, 3), equals(max(1:10, hex[,-5], -3)))

  Log.info("min and max corretness")
  df <- data.frame(c(1,-0.1,0))
  expect_that(min(as.h2o(conn, df)), equals(min(df)))
  expect_that(max(as.h2o(conn, df)), equals(max(df)))
  
  testEnd()
}

doTest("EQ2 Tests: min and max", test.basic.minmax)

