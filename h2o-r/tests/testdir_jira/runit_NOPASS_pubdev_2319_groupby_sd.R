setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
test.pub.2319 <- function() {
  h2oTest.heading("BEGIN TEST")
  
  hex <- as.h2o(iris)

  gp <- h2o.group_by(data = hex, by = "Species", sd("Sepal.Length"))
  
  s1 <- sd(hex[hex$Species == "setosa", "Sepal.Length"])
  s2 <- sd(hex[hex$Species == "versicolor", "Sepal.Length"])
  s3 <-sd(hex[hex$Species == "virginica", "Sepal.Length"])
  
  expect_equal(gp[ gp[,1] == "setosa", 2], s1)
  expect_equal(gp[ gp[,1] == "versicolor", 2], s2)
  expect_equal(gp[ gp[,1] == "virginica", 2], s3)
}

h2oTest.doTest("PUBDEV-2319", test.pub.2319)
