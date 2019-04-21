setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
#----------------------------------------------------------------------
# Purpose:  This test to make sure we fixed the off-by-one bug in
# PUBDEV-4438 is fixed.
#----------------------------------------------------------------------

test <- function() {
  tolerance=1e-12
  data(iris)
  iris<-rbind(iris,iris,iris,iris,iris,iris,iris,iris,iris,iris,iris,iris,iris,iris,iris,iris)
  iris_h2o<-as.h2o(iris)
  expect_true(abs(iris[2,1]-iris_h2o[2,1]) < tolerance)

  # assignment and things changed a bit, first column, second row of Sepal.Length changes from 4.9 to 5.1
  iris_h2o[c(1:1001),"Sepal.Length"]<-iris_h2o[c(1:1001),"Sepal.Length"]
  expect_true(abs(iris[2,1]-iris_h2o[2,1]) < tolerance)
}

doTest("Off_by_one_error", test)
