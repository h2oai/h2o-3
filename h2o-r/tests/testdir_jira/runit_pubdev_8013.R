setwd(normalizePath(dirname(R.utils::commandArgs(asValues = TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.proper_x_extraction <- function() {
  train <- h2o.importFile(path = locate("smalldata/wine/winequality-redwhite-no-BOM.csv"))
  x <- c("citric acid", "residual sugar", "chlorides", "free sulfur dioxide", "total sulfur dioxide", "density", "pH")
  y <- "quality"
  gbm <- h2o.gbm(x = x, y = y, training_frame = train, offset_column = "sulphates", weights_column = "alcohol", fold_column = "type")

  expect_true(setequal(gbm@parameters$x, x))
  expect_equal(gbm@parameters$y, y)
  expect_true(setequal(gbm@allparameters$x, x))
  expect_equal(gbm@allparameters$y, y)

  # test it works also without any "special" column specified
  gbm2 <- h2o.gbm(x = x, y = y, training_frame = train)

  expect_true(setequal(gbm2@parameters$x, x))
  expect_equal(gbm2@parameters$y, y)
  expect_true(setequal(gbm2@allparameters$x, x))
  expect_equal(gbm2@allparameters$y, y)
}

doTest("Test proper x and y extraction", test.proper_x_extraction)
