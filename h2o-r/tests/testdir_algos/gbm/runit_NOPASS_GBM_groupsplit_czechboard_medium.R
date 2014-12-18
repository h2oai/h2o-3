setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.GBM.Czechboard <- function(conn) {
  # Training set has checkerboard pattern
  Log.info("Importing czechboard_300x300.csv data...\n")
  board.hex <- h2o.uploadFile(conn, locate("smalldata/gbm_test/czechboard_300x300.csv"), key = "board.hex")
  board.hex[,3] <- as.factor(board.hex[,3])
  Log.info("Summary of czechboard_300x300.csv from H2O:\n")
  print(summary(board.hex))
  
  # Train H2O GBM Model:
  Log.info("H2O GBM (Naive Split) with parameters:\nntrees = 50, max_depth = 20, nbins = 500\n")
  drfmodel.nogrp <- h2o.gbm(x = c("C1", "C2"), y = "C3", training_frame = board.hex, ntrees = 50, max_depth = 20, nbins = 500, group_split = FALSE)
  print(drfmodel.nogrp)
  
  Log.info("H2O GBM (Group Split) with parameters:\nntrees = 50, max_depth = 20, nbins = 500\n")
  drfmodel.grpsplit <- h2o.gbm(x = c("C1", "C2"), y = "C3", training_frame = board.hex, ntrees = 50, max_depth = 20, nbins = 500, group_split = TRUE)
  print(drfmodel.grpsplit)
  
  expect_true(drfmodel.grpsplit@model$auc >= drfmodel.nogrp@model$auc)
  expect_true(drfmodel.grpsplit@model$confusion[3,3] <= drfmodel.nogrp@model$confusion[3,3])
  
  testEnd()
}

doTest("GBM Test: Classification with Checkerboard Group Split", test.GBM.Czechboard)
