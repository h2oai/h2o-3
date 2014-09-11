setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../findNSourceUtils.R')

test.DRF.Czechboard <- function(conn) {
  # Training set has checkerboard pattern
  Log.info("Importing czechboard_300x300.csv data...\n")
  board.hex <- h2o.uploadFile(conn, locate("smalldata/histogram_test/czechboard_300x300.csv"), key = "board.hex")
  board.hex[,3] <- as.factor(board.hex[,3])
  Log.info("Summary of czechboard_300x300.csv from H2O:\n")
  print(summary(board.hex))
  
  # Train H2O DRF Model:
  Log.info("H2O DRF (Naive Split) with parameters:\nclassification = TRUE, ntree = 50, depth = 20, nbins = 500\n")
  drfmodel.nogrp <- h2o.randomForest(x = c("C1", "C2"), y = "C3", data = board.hex, classification = TRUE, ntree = 50, depth = 20, nbins = 500, doGrpSplit = FALSE, type = "BigData")
  print(drfmodel.nogrp)
  
  Log.info("H2O DRF (Group Split) with parameters:\nclassification = TRUE, ntree = 50, depth = 20, nbins = 500\n")
  drfmodel.grpsplit <- h2o.randomForest(x = c("C1", "C2"), y = "C3", data = board.hex, classification = TRUE, ntree = 50, depth = 20, nbins = 500, doGrpSplit = TRUE, type = "BigData")
  print(drfmodel.grpsplit)
  
  expect_true(drfmodel.grpsplit@model$auc >= drfmodel.nogrp@model$auc)
  expect_true(drfmodel.grpsplit@model$confusion[3,3] <= drfmodel.nogrp@model$confusion[3,3])
  
  testEnd()
}

doTest("DRF Test: Classification with Checkerboard Group Split", test.DRF.Czechboard)
