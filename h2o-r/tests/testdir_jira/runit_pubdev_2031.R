setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pubdev.2031 <- function(conn){

  #cars.hex <- h2o.uploadFile("smalldata/junit/cars.csv")
  #seed <- 279825333
  #sid <- h2o.runif(cars.hex,seed=seed)
  #cars.train <- h2o.assign(cars.hex[c.sid > .2, ], "cars.train")
  cars.train <- h2o.uploadFile(h2oTest.locate("smalldata/jira/cars_train.csv"))
  x <- c(4, 5, 6, 7)
  y <- 3
  distribution <- "gaussian"
  ntrees <- 32
  max_depth <- 15
  min_rows <- 2
  h2o.gbm(x=x, y=y, training_frame=cars.train, distribution=distribution, ntrees=ntrees, max_depth=max_depth,
          min_rows=min_rows)

  
}

h2oTest.doTest("PUBDEV-2031", test.pubdev.2031)
