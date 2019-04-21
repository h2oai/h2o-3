setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pub.4757 <- function() {
  mtcars_4cyl <- subset(mtcars, cyl == 4)
  mtcars_4cyl.hex <- as.h2o(mtcars_4cyl)
  mtcars_4cyl.hex$cyl <- as.factor(mtcars_4cyl.hex$cyl)
  mtcars_4cyl.hex$cyl_replace <- ifelse(mtcars_4cyl.hex$cyl == '4', '3', '2')

  expect_equal(h2o.levels(mtcars_4cyl.hex$cyl_replace), "3")
}

doTest("Test pub 4757", test.pub.4757)
