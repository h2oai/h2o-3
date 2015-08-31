setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.glrm.unitonesparse <- function() {
  m <- 1000; n <- 100; k <- 10
  Log.info(paste("Uploading random uniform matrix with rows =", m, "and cols =", n))
  Y <- matrix(runif(k*n), nrow = k, ncol = n)
  X <- matrix(0, nrow = m, ncol = k)
  for(i in 1:nrow(X)) X[i,sample(1:ncol(X), 1)] <- 1
  train <- X %*% Y
  train.h2o <- as.h2o(train)
  
  Log.info("Run GLRM with unit one-sparse regularization on X")
  # initY <- Y + 0.1*matrix(runif(k*n,-1,1), nrow = k, ncol = n)
  initY <- matrix(runif(k*n), nrow = k, ncol = n)
  fitH2O <- h2o.glrm(train.h2o, init = initY, loss = "L2", regularization_x = "UnitOneSparse", gamma_x = 1, gamma_y = 0)
  Log.info(paste("Iterations:", fitH2O@model$iterations, "\tFinal Objective:", fitH2O@model$objective))
  fitY <- t(fitH2O@model$archetypes)
  fitX <- h2o.getFrame(fitH2O@model$loading_key$name)

  Log.info("Check that X matrix consists of rows of basis vectors")
  fitX.mat <- as.matrix(fitX)
  is_basis <- apply(fitX.mat, 1, function(x) { 
                      ones <- length(which(x == 1))
                      zeros <- length(which(x == 0))
                      ones == 1 && (zeros + ones) == length(x)
                    })
  expect_true(all(is_basis))
  expect_equal(sum((train - fitX.mat %*% fitY)^2), fitH2O@model$objective)
  testEnd()
}

doTest("GLRM Test: Unit One-sparse K-means Implementation", test.glrm.unitonesparse)
