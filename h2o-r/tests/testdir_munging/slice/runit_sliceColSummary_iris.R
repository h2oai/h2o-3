setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

#setupRandomSeed(1193410486)
test.slice.colSummary <- function() {
  Log.info("Importing iris.csv data...\n")
  iris.hex <- h2o.importFile(locate("smalldata/iris/iris_wheader.csv"), "iris.hex")
  Log.info("Check that summary works...")
  
  summary(iris.hex)
  summary_ <- summary(iris.hex)
  iris_nrows <- nrow(iris.hex)
  iris_ncols <- ncol(iris.hex)

  Log.info("Check that iris is 150x5")
  Log.info(paste("Got: nrows = ", iris_nrows, sep =""))
  Log.info(paste("Got: ncols = ", iris_ncols, sep =""))
  
  expect_that(iris_nrows, equals(150))
  expect_that(iris_ncols, equals(5))
  
  sepalLength <- iris.hex[,1]
  Log.info("Summary on the first column:\n")
  expect_that(sepalLength, is_a("Frame"))
 
  print(summary(sepalLength))
  Log.info("try mean")
  m <- mean(sepalLength)
  cat("\nH2O mean: ", m, "    R mean: ", mean(iris$Sepal.Length), "\n")
  
  expect_that(m, equals(mean(iris$Sepal.Length)))
  Log.info("Try mean, min, max, sd, and compare to actual:\n")
  stats_ <- list("mean"=mean(sepalLength), "min"=min(sepalLength), "max"=max(sepalLength), "sd"=sd(sepalLength))
  stats  <- list("mean"=mean(iris[,1]), "min"=min(iris[,1]), "max"=max(iris[,1]), "sd"=sd(iris[,1]))
  
  Log.info("Actual mean, min, max, sd:\n")
  Log.info(stats)
  cat("\n")
  Log.info("H2O-R's mean, min, max, sd: \n")
  Log.info(stats_)
  cat("\n")
  expect_that(unlist(stats),equals(unlist(stats_)))
  testEnd()
}

doTest("Slice Tests: Column Summary", test.slice.colSummary)

