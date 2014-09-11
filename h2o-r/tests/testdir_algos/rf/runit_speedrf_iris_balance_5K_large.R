setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../findNSourceUtils.R')

test.speedrf.balance.5K <- function(conn) {
  iris.hex <- h2o.uploadFile(conn, locate( "smalldata/iris/iris.csv"), "iris.hex")
  iris.rf  <- h2o.randomForest(y = 5, x = 1:4, data = iris.hex, ntree = 5000, depth = 100)
  print(iris.rf)
  print(iris.rf@model$confusion)
  print(iris.rf@model$confusion[4,1:3])

  v <- iris.rf@model$confusion[4,1:3]
  
  expect_that(abs(v[1] - 50), is_less_than(5))
  expect_that(abs(v[2] - 50), is_less_than(5))
  expect_that(abs(v[3] - 50), is_less_than(5))
  testEnd()
}

doTest("speedrf balance on 5K trees, should get nearly perfect...", test.speedrf.balance.5K)

