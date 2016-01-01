setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test <- function(){
  ## Helper functions
  # Function to standardize data
  standardizeVec <- function(v) {(v - mean(v))/sd(v)}
  standardizeH2OFrame <- function(X) {
    X2 <- X
    for(i in seq(1,ncol(X)-1))
      X2[,i] <- standardizeVec(X2[,i])
    X2
  }
  # Functions to calculate logistic gradient
  logistic_gradient <- function(x,y,beta) {
    y <- -1 + 2*y
    eta <- x %*% beta
    d <- 1 + exp(-y*eta)
    grad <- -y * (1-1.0/d)
    t(grad) %*% x
  }
  # no L1 here, alpha is 0
  h2o_logistic_gradient <- function(x,y,beta,beta_given,rho,lambda) {
    grad <- logistic_gradient(x,y,beta)/nrow(x) + (beta - beta_given)*rho + lambda*beta
    grad
  }

  ## Import data
  h2oData <- h2o.importFile("/mnt/0xcustomer-datasets/c27/data.csv")
  betaConstraints <- h2o.importFile("/mnt/0xcustomer-datasets/c27/constraints_indices.csv")
  betaConstraints <- betaConstraints[1:(nrow(betaConstraints)-1),] # remove intercept
  betaConstraints <- as.data.frame(betaConstraints)

  ## Set Parameters
  indVars <-  as.character(betaConstraints$names[1:nrow(betaConstraints)])
  depVars <- "C3"
  lambda <- 0
  alpha <- 0
  family_type <- "binomial"

  ## Take subset of data
  h2oTest.logInfo("Subset dataset to only predictor and response variables...")
  h2oData <- h2oData[,c(indVars, depVars)]
  summary(h2oData)

  ## Run full H2O GLM with Bayesian priors vs no priors
  h2oTest.logInfo("Run a logistic regression with no regularization and alpha = 0 and beta constraints with priors. ")
  glm_bayesianp <- h2o.glm(x = indVars, y = depVars, training_frame = h2oData, family = family_type, lambda = lambda,
                           alpha = alpha, beta_constraints = betaConstraints)

  h2oTest.logInfo("Run a logistic regression with no regularization and alpha = 0 and beta constraints without priors. ")
  glm_nopriors <- h2o.glm(x = indVars, y = depVars, training_frame = h2oData, family = family_type, lambda = lambda,
                          alpha = alpha, beta_constraints = betaConstraints[c("names","lower_bounds","upper_bounds")])


  ## Standardize Data Set
  h2oTest.logInfo("Standardize Data in R: ")
  data.df <- as.data.frame(h2oData)
  data.standardize <- standardizeH2OFrame(data.df)
  ## check standardization is done correctly
  checkEqualsNumeric(apply(data.standardize[,1:22], 2, mean), rep(0, 22), 1E-10)
  checkEqualsNumeric(apply(data.standardize[,1:22], 2, sd), rep(1, 22), 1E-10)
  ## Seperate to x and y matrices
  y <- as.matrix(data.standardize[,depVars])
  x <- cbind(as.matrix(data.standardize[,indVars]),1)

  h2oTest.logInfo("Calculate the gradient: ")
  beta1 <- glm_bayesianp@model$coefficients_table[,3]
  beta2 <- glm_nopriors@model$coefficients_table[,3]
  ## Standardize beta given
  beta_given.df <- as.data.frame(betaConstraints$beta_given)
  col_sd <- apply(data.df[,1:22], 2, sd)
  beta_given <- beta_given.df[,1]*col_sd
  lambda <- glm_bayesianp@allparameters$lambda
  rho <- c(rep(1,22),0)
  beta <- c(beta_given,0)
  gradient1 <- h2o_logistic_gradient(x,y,beta = beta1, beta_given = beta, rho= rho, lambda)
  gradient2 <- h2o_logistic_gradient(x,y,beta = beta2, beta_given = beta, rho= 0, lambda)

  h2oTest.logInfo("Check gradient of beta constraints with priors or beta given...")
  print(gradient1)
  if(!all(gradient1 < 1E-8)) stop(paste0("Gradient from model output > ", 1E-8))

  h2oTest.logInfo("Check gradient of beta constraints without priors or beta given...")
  print(gradient2)
  if(!all(gradient2 < 1E-4)) stop(paste0("Gradient from model output > ", 1E-4))
}

h2oTest.doTest("GLM Test: Bayesian Priors with Standardization = F: ", test)
