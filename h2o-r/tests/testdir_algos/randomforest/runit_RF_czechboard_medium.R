setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.DRF.Czechboard <- function() {
  # Training set has checkerboard pattern
  Log.info("Importing czechboard_300x300.csv data...\n")
  board.hex <- h2o.uploadFile( locate("smalldata/gbm_test/czechboard_300x300.csv"), destination_frame = "board.hex")
  board.hex[,3] <- as.factor(board.hex[,3])

  Log.info("Summary of czechboard_300x300.csv from H2O:\n")
  print(summary(board.hex))

  # Train H2O DRF Model:
  Log.info("H2O DRF (Group Split) with parameters:\nclassification = TRUE, ntree = 50, depth = 20, nbins = 500\n")
  drfmodel<- h2o.randomForest(x = c("C1", "C2"), y = "C3",
                                     training_frame = board.hex, ntrees = 50,
                                     max_depth = 20, min_rows = 500)
  print(drfmodel)
  testEnd()
}

doTest("DRF Test: Classification with Checkerboard Group Split", test.DRF.Czechboard)
