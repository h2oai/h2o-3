


test.glrm.nnmf <- function() {
  m <- 1000; n <- 100; k <- 10
  Log.info(paste("Uploading random uniform matrix with rows =", m, "and cols =", n))
  Y <- matrix(runif(k*n), nrow = k, ncol = n)
  X <- matrix(runif(m*k), nrow = m, ncol = k)
  train <- X %*% Y
  train.h2o <- as.h2o(train)
  
  Log.info("Run GLRM with non-negative regularization")
  # initY <- Y + 0.1*matrix(runif(k*n,-1,1), nrow = k, ncol = n)
  initY <- matrix(runif(k*n), nrow = k, ncol = n)
  fitH2O <- h2o.glrm(train.h2o, k = k, init = "User", user_y = initY, loss = "Quadratic", regularization_x = "NonNegative", regularization_y = "NonNegative", gamma_x = 1, gamma_y = 1)
  Log.info(paste("Iterations:", fitH2O@model$iterations, "\tFinal Objective:", fitH2O@model$objective))
  fitY <- as.matrix(fitH2O@model$archetypes)
  fitX <- h2o.getFrame(fitH2O@model$representation_name)

  Log.info("Check that X and Y matrices are non-negative")
  fitX.mat <- as.matrix(fitX)
  expect_true(all(fitY >= 0))
  expect_true(all(fitX.mat >= 0))
  
  Log.info("Check final objective function value")
  fitXY <- fitX.mat %*% fitY
  expect_equal(sum((train - fitXY)^2), fitH2O@model$objective)
  
  Log.info("Impute XY and check error metrics")
  pred <- predict(fitH2O, train.h2o)
  expect_equivalent(as.matrix(pred), fitXY)   # Imputation for numerics with quadratic loss is just XY product
  expect_equal(fitH2O@model$training_metrics@metrics$numerr, fitH2O@model$objective, 1e-5)
  expect_equal(fitH2O@model$training_metrics@metrics$caterr, 0)
  
}

doTest("GLRM Test: Non-negative Matrix Factorization", test.glrm.nnmf)
