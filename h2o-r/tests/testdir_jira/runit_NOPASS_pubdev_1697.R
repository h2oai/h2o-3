setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.pubdev.1697 <- function(conn) {
  cars = h2o.importFile(locate("smalldata/junit/cars_20mpg.csv"))
  cars$economy_20mpg = as.factor(cars$economy_20mpg)
  gbm = h2o.gbm(y="economy_20mpg", x=c("displacement","power","weight","acceleration","year"), training_frame=cars,
                nfolds=nrow(cars), distribution="bernoulli")

  testEnd()
}

doTest("PUBDEV-1697: Cross Validation: Job not found", test.pubdev.1697)
