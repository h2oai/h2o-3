setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pubdev.1976.negative.alpha <- function(conn){

  cars <- h2o.uploadFile(h2oTest.locate("smalldata/junit/cars_20mpg.csv"))
  predictors <- c("displacement","power","weight","acceleration","year")
  response_col <- "cylinders"
  e <- tryCatch( h2o.glm(x=predictors, y=response_col, training_frame=cars, alpha=-22), error = function(x) x)
  expect_false("'null'" %in% e[[1]])

  
}

h2oTest.doTest("PUBDEV-1976", test.pubdev.1976.negative.alpha)
