setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pubdev.1950.rapids <- function(conn){

  cars <- h2o.uploadFile(h2oTest.locate("smalldata/junit/cars_20mpg.csv"))
  r <- h2o.runif(cars,seed=874447)
  train <- cars[r > 0.2,]
  valid <- cars[r <= 0.2,]
  h2o.getFrame(valid@frame_id)

  
}

h2oTest.doTest("PUBDEV-1950", test.pubdev.1950.rapids)

