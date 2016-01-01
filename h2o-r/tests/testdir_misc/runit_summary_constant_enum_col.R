setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.summary <- function() {
  iris.hex <- as.h2o(iris)

  setosa <- iris.hex[iris.hex$Species == "setosa",]
  print(summary(setosa))
  setosa_summary <- summary(setosa)
  expect_true(grepl("50", setosa_summary[1,"Species"]))
  expect_true(grepl("0", setosa_summary[2,"Species"]))
  expect_true(grepl("0", setosa_summary[3,"Species"]))

  versicolor <- iris.hex[iris.hex$Species == "versicolor",]
  print(summary(versicolor))
  versicolor_summary <- summary(versicolor)
  expect_true(grepl("50", versicolor_summary[1,"Species"]))
  expect_true(grepl("0", versicolor_summary[2,"Species"]))
  expect_true(grepl("0", versicolor_summary[3,"Species"]))

  virginica <- iris.hex[iris.hex$Species == "virginica",]
  print(summary(virginica))
  virginica_summary <- summary(virginica)
  expect_true(grepl("50", virginica_summary[1,"Species"]))
  expect_true(grepl("0", virginica_summary[2,"Species"]))
  expect_true(grepl("0", virginica_summary[3,"Species"]))

  
}

h2oTest.doTest("Summary on frame with constant enum columns", test.summary)
