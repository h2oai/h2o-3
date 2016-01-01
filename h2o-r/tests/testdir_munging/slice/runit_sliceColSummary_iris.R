setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



#setupRandomSeed(1193410486)
test.slice.colSummary <- function() {
  h2oTest.logInfo("Importing iris.csv data...\n")
  iris.hex <- h2o.importFile(h2oTest.locate("smalldata/iris/iris_wheader.csv"), "iris.hex")
  h2oTest.logInfo("Check that summary works...")
  
  summary(iris.hex)
  summary_ <- summary(iris.hex)
  iris_nrows <- nrow(iris.hex)
  iris_ncols <- ncol(iris.hex)

  h2oTest.logInfo("Check that iris is 150x5")
  h2oTest.logInfo(paste("Got: nrows = ", iris_nrows, sep =""))
  h2oTest.logInfo(paste("Got: ncols = ", iris_ncols, sep =""))
  
  expect_that(iris_nrows, equals(150))
  expect_that(iris_ncols, equals(5))
  
  sepalLength <- iris.hex[,1]
  h2oTest.logInfo("Summary on the first column:\n")
  expect_that(sepalLength, is_a("H2OFrame"))
 
  print(summary(sepalLength))
  h2oTest.logInfo("try mean")
  m <- mean(sepalLength)
  cat("\nH2O mean: ", m, "    R mean: ", mean(iris$Sepal.Length), "\n")
  
  expect_that(m, equals(mean(iris$Sepal.Length)))
  h2oTest.logInfo("Try mean, min, max, sd, and compare to actual:\n")
  stats_ <- list("mean"=mean(sepalLength), "min"=min(sepalLength), "max"=max(sepalLength), "sd"=sd(sepalLength))
  stats  <- list("mean"=mean(iris[,1]), "min"=min(iris[,1]), "max"=max(iris[,1]), "sd"=sd(iris[,1]))
  
  h2oTest.logInfo("Actual mean, min, max, sd:\n")
  h2oTest.logInfo(stats)
  cat("\n")
  h2oTest.logInfo("H2O-R's mean, min, max, sd: \n")
  h2oTest.logInfo(stats_)
  cat("\n")
  expect_that(unlist(stats),equals(unlist(stats_)))
  
}

h2oTest.doTest("Slice Tests: Column Summary", test.slice.colSummary)

