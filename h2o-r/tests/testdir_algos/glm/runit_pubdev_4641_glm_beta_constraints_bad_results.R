setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# add test from Erin Ledell
glmBetaConstraints <- function() {
  df <- h2o.importFile(locate("smalldata/higgs/higgs_train_10k.csv"))
  test <- h2o.importFile(locate("smalldata/higgs/higgs_test_5k.csv"))

  y <- "response"
  x <- setdiff(names(df), y)
  df[,y] <- as.factor(df[,y])
  test[,y] <- as.factor(test[,y])
  
  # Split off a validation_frame
  ss <- h2o.splitFrame(df, seed = 1)
  train <- ss[[1]]
  valid <- ss[[2]]
  
  # Some comparisons
  m4 <- h2o.glm(x = x, y = y, training_frame = train, validation_frame = valid, family = "binomial", non_negative = TRUE, solver="COORDINATE_DESCENT")
  m5 <- h2o.glm(x = x, y = y, training_frame = train, validation_frame = valid, family = "binomial", non_negative = TRUE, lambda_search = TRUE, solver="COORDINATE_DESCENT")
  m6 <- h2o.glm(x = x, y = y, training_frame = train, validation_frame = valid, family = "binomial", non_negative = TRUE, lambda_search = TRUE, solver="COORDINATE_DESCENT", cold_start=TRUE)
  m10 <- h2o.glm(x = x, y = y, training_frame = train, validation_frame = valid, family = "binomial", solver="irlsm", non_negative=TRUE)
  m11 <- h2o.glm(x = x, y = y, training_frame = train, validation_frame = valid, family = "binomial", solver="irlsm", non_negative=TRUE, lambda_search=TRUE)
  m12 <- h2o.glm(x = x, y = y, training_frame = train, validation_frame = valid, family = "binomial", solver="irlsm", non_negative=TRUE, lambda_search=TRUE, cold_start=TRUE)
  m16 <- h2o.glm(x = x, y = y, training_frame = train, family = "binomial", non_negative = TRUE, solver="COORDINATE_DESCENT")
  m17 <- h2o.glm(x = x, y = y, training_frame = train, family = "binomial", non_negative = TRUE, lambda_search = TRUE, solver="COORDINATE_DESCENT")
  m18 <- h2o.glm(x = x, y = y, training_frame = train, family = "binomial", non_negative = TRUE, lambda_search = TRUE, solver="COORDINATE_DESCENT", cold_start=TRUE)
  m22 <- h2o.glm(x = x, y = y, training_frame = train, family = "binomial", solver="irlsm", non_negative=TRUE)
  m23 <- h2o.glm(x = x, y = y, training_frame = train, family = "binomial", solver="irlsm", non_negative=TRUE, lambda_search=TRUE)
  m24 <- h2o.glm(x = x, y = y, training_frame = train, family = "binomial", solver="irlsm", non_negative=TRUE, lambda_search=TRUE, cold_start=TRUE)
  
  
  models <- c(m4, m5, m6, m10, m11, m12, m16, m17, m18, m22, m23, m24)
  
  for (m in models) {
    cat(sprintf("validation_frame: %s\n", m@parameters$validation_frame))
    cat(sprintf("lambda_search: %s\n", m@parameters$lambda_search))
    cat(sprintf("non_negative: %s\n", m@parameters$non_negative))
    cat(sprintf("solver: %s\n", m@parameters$solver))
    cat(sprintf("coldstart: %s\n", m@parameters$cold_start))    
    cat("-------------------------\n")
    cat(sprintf("Test AUC: %f\n", h2o.auc(h2o.performance(m, test))))
    cat(sprintf("Test Logloss: %f\n", h2o.logloss(h2o.performance(m, test))))
    cat(sprintf(
      "Test Res Deviance: %f\n\n",
      h2o.residual_deviance(h2o.performance(m, test))
    ))
    # check coefficients are non-negative
    coeff <- m@model$coefficients
    count <- 1
    for (oneCoeff in coeff) {
      if (count > 1) {
        expect_true(oneCoeff >= 0)
      }
      count <- count+1
    }
  } 
}

doTest("GLM: Compare GLM with and without beta constraints for irlsm and coordinate_descent", glmBetaConstraints)
