setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pubdev.1697 <- function() {
  cars = h2o.importFile(h2oTest.locate("smalldata/junit/cars_20mpg.csv"))
  cars$economy_20mpg = as.factor(cars$economy_20mpg)
  gbm = h2o.gbm(y="economy_20mpg", x=c("displacement","power","weight","acceleration","year"), training_frame=cars,
                nfolds=nrow(cars), distribution="bernoulli", fold_assignment="Modulo", ntrees=2)

  
}

h2oTest.doTest("PUBDEV-1697: Cross Validation: Job not found", test.pubdev.1697)
