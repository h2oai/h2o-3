setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.glrm.transform <- function() {
  m <- 1000; n <- 100; k <- 10
  Log.info(paste("Uploading random uniform matrix with rows =", m, "and cols =", n))
  Y <- matrix(runif(k*n), nrow = k, ncol = n)
  X <- matrix(runif(m*k), nrow = m, ncol = k)
  train <- X %*% Y
  train.h2o <- as.h2o(train)
  test.h2o <- as.h2o(train)

  fitH2O <- h2o.glrm(training_frame=train.h2o, k = k, loss = "Quadratic", seed=12345)
  pred1 <- h2o.predict(fitH2O, test.h2o)
  transform1 <- h2o.transform_frame(fitH2O, test.h2o)
  fitH2O2 <- h2o.glrm(training_frame=train.h2o, k = k, loss = "Quadratic", seed=12345)
  transform2 <- h2o.transform_frame(fitH2O2, test.h2o)
  
  compareFrames(transform1, transform2, prob=1, tolerance=1e-6)
}

doTest("GLRM Test: transform_frame - return X part of prediction", test.glrm.transform)
