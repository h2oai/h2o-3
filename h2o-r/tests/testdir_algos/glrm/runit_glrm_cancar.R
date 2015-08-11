setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.pubdev.1725 <- function(conn) {
  Log.info("Importing cancar.csv data...")
  cancar.hex <- h2o.importFile(locate("smalldata/glrm_test/cancar.csv"))
  print(summary(cancar.hex))
  
  Log.info("Building GLRM model with init = 'PlusPlus'")
  fitH2O_pp <- h2o.glrm(cancar.hex, k = 4, transform = "NONE", init = "PlusPlus", loss = "L2", gamma_x = 0, gamma_y = 0, max_iterations = 1000)
  Log.info(paste("Total Iterations:", fitH2O_pp@model$iterations))
  Log.info(paste("Final Objective:", fitH2O_pp@model$objective))
  
  Log.info("Building GLRM model with init = 'SVD'")
  fitH2O_svd <- h2o.glrm(cancar.hex, k = 4, transform = "NONE", init = "SVD", loss = "L2", gamma_x = 0, gamma_y = 0, max_iterations = 1000)
  Log.info(paste("Total Iterations:", fitH2O_svd@model$iterations))
  Log.info(paste("Final Objective:", fitH2O_svd@model$objective))
  testEnd()
}

doTest("PUBDEV-1725: GLRM poor fit with k-means++ initialization", test.pubdev.1725)
