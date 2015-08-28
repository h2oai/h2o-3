setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.pubdev.1776 <- function(conn) {

  cars <- h2o.importFile(path=locate("smalldata/junit/cars_20mpg.csv"))
  predictors <- c("displacement","power","weight","acceleration","year")
  response_col <- "economy_20mpg"
  family <- "binomial"
  cars[response_col] <- as.factor(cars[response_col])
  glm <- h2o.glm(y=response_col, x=predictors, training_frame=cars, nfolds=nrow(cars), family=family, fold_assignment="Modulo")

  testEnd()
}

doTest("PUBDEV-1776", test.pubdev.1776)
