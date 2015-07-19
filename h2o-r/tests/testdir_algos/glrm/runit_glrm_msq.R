setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.pubdev.1692 <- function(conn) {
  Log.info("Importing glrm_test/msq.csv data...")
  msq.dat <- read.csv(locate("smalldata/glrm_test/msq.csv"), header = FALSE)
  msq.hex <- h2o.importFile(locate("smalldata/glrm_test/msq.csv"), header = FALSE)
  print(summary(msq.hex))
  k <- 10
  
  Log.info("Running GLRM with transform = 'NONE', loss = 'L2', gamma_x = gamma_y = 0")
  # init <- msq.dat[1:k,]
  init <- "SVD"
  fitH2O <- h2o.glrm(msq.hex, k = k, transform = "NONE", init = init, loss = "L2", gamma_x = 0, gamma_y = 0, max_iterations = 1000)
  Log.info(paste("Total Iterations:", fitH2O@model$iterations))
  Log.info(paste("Final Objective:", fitH2O@model$objective))
  expect_true(fitH2O@model$objective <= 101000)    # Should be roughly as good as Madeleine's Julia code
  
  Log.info("Running GLRM with transform = 'STANDARDIZE', loss = 'L2', gamma_x = gamma_y = 0")
  # init_scale <- scale(msq.dat, center = TRUE, scale = TRUE)[1:k,]
  init_scale <- "SVD"
  fitH2O_scale <- h2o.glrm(msq.hex, k = k, transform = "STANDARDIZE", init = init_scale, loss = "L2", gamma_x = 0, gamma_y = 0, max_iterations = 1000)
  Log.info(paste("Total Iterations:", fitH2O_scale@model$iterations))
  Log.info(paste("Final Objective:", fitH2O_scale@model$objective))
  # expect_true(fitH2O@model$objective <= 6000)
  
  testEnd()
}

doTest("PUBDEV-1692: GLRM final objective too large", test.pubdev.1692)
