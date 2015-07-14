setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.glrm.orthonnmf <- function(conn) {
  m <- 1000; n <- 100; k <- 10
  Log.info(paste("Uploading random uniform matrix with rows =", m, "and cols =", n))
  Y <- matrix(runif(k*n), nrow = k, ncol = n)
  X <- matrix(runif(m*k), nrow = m, ncol = k)
  train <- X %*% Y
  train.h2o <- as.h2o(conn, train)
  
  Log.info("Run GLRM with orthogonal non-negative regularization on X, non-negative regularization on Y")
  initY <- matrix(runif(k*n), nrow = k, ncol = n)
  fitH2O <- h2o.glrm(train.h2o, init = initY, loss = "L2", regularization_x = "OneSparse", regularization_y = "NonNegative", gamma_x = 1, gamma_y = 1)
  Log.info(paste("Iterations:", fitH2O@model$iterations, "\tFinal Objective:", fitH2O@model$objective))
  fitY <- t(fitH2O@model$archetypes)
  fitX <- h2o.getFrame(fitH2O@model$loading_key$name)
  
  Log.info("Check that X and Y matrices are non-negative")
  fitX.mat <- as.matrix(fitX)
  expect_true(all(fitY >= 0))
  expect_true(all(fitX.mat >= 0))
  
  Log.info("Check that columns of X are orthogonal")
  XtX <- t(fitX.mat) %*% fitX.mat
  expect_true(all(XtX[!diag(nrow(XtX))] == 0))
  expect_equal(sum((train - fitX.mat %*% fitY)^2), fitH2O@model$objective)
  
  Log.info("Run GLRM with orthogonal non-negative regularization on both X and Y")
  fitH2O <- h2o.glrm(train.h2o, init = initY, loss = "L2", regularization_x = "OneSparse", regularization_y = "OneSparse", gamma_x = 1, gamma_y = 1)
  Log.info(paste("Iterations:", fitH2O@model$iterations, "\tFinal Objective:", fitH2O@model$objective))
  fitY <- t(fitH2O@model$archetypes)
  fitX <- h2o.getFrame(fitH2O@model$loading_key$name)
  
  Log.info("Check that X and Y matrices are non-negative")
  fitX.mat <- as.matrix(fitX)
  expect_true(all(fitY >= 0))
  expect_true(all(fitX.mat >= 0))
  
  Log.info("Check that columns of X are orthogonal")
  XtX <- t(fitX.mat) %*% fitX.mat
  expect_true(all(XtX[!diag(nrow(XtX))] == 0))
  
  Log.info("Check that rows of Y are orthogonal")
  YYt <- fitY %*% t(fitY)
  expect_true(all(YYt[!diag(nrow(YYt))] == 0))
  expect_equal(sum((train - fitX.mat %*% fitY)^2), fitH2O@model$objective)
  testEnd()
}

doTest("GLRM Test: Orthogonal Non-negative Matrix Factorization", test.glrm.orthonnmf)
