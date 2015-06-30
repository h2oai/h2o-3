setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test <- function(conn){
  ## Import data
  if (!file.exists("/mnt/0xcustomer-datasets/c27/data.csv")) {
        Log.info("h2o-only data")
        testEnd()
  } else {
    h2oData <- h2o.importFile(conn, "/mnt/0xcustomer-datasets/c27/data.csv")
    betaConstraints <- h2o.importFile(conn, "/mnt/0xcustomer-datasets/c27/constraints_indices.csv")
    betaConstraints <- betaConstraints[1:(nrow(betaConstraints)-1),] # remove intercept
    betaConstraints <- as.data.frame(betaConstraints)

    ## Set Parameters (default standardization = T)

    indVars <-  as.character(betaConstraints$names[1:nrow(betaConstraints)])
    depVars <- "C3"
    totRealProb <- 0.002912744
    lambda <- 0
    alpha <- 0
    family_type <- "binomial"

    ## Take subset of data
    Log.info("Subset dataset to only predictor and response variables...")
    h2oData <- h2oData[,c(depVars,indVars)]
    summary(h2oData)

    ## Run full H2O GLM with and without priors
    Log.info("Run a logistic regression with no regularization and alpha = 0 and beta constraints without priors.")
    glm_nopriors <- h2o.glm(x = indVars, y = depVars, training_frame = h2oData, family = family_type,
                            standardize = T, lambda = lambda, alpha = alpha,
                            beta_constraints = betaConstraints)
    Log.info("Run a logistic regression with no regularization and alpha = 0 and beta constraints with prior =
              total real probability.")
    glm_priors <- h2o.glm(x = indVars, y = depVars, training_frame = h2oData, family = family_type, prior = totRealProb,
                          standardize = T, lambda = lambda, alpha = alpha, beta_constraints = betaConstraints)


    ## Check coefficients remained the same and the intercept is adjusted
    coeff1 <- glm_priors@model$coefficients[-ncol(h2oData)]
    coeff2 <- glm_nopriors@model$coefficients[-ncol(h2oData)]
    intercept1 <- glm_priors@model$coefficients["Intercept"]
    intercept2 <- glm_nopriors@model$coefficients["Intercept"]
    print("Coefficients from GLM ran with priors: ")
    print(coeff1)
    print("Coefficients from GLM ran without priors: ")
    print(coeff2)
    ymean <- mean(h2oData[,depVars])
    adjustment <- -log(ymean*(1-totRealProb)/(totRealProb*(1-ymean)))
    intercept2adj <- intercept1-adjustment
    checkEqualsNumeric(coeff1, coeff2, tolerance = 0)
    checkEqualsNumeric(intercept2, intercept2adj, tolerance = 1E-10)

    testEnd()
  }
}

doTest("GLM Test: Beta Constraints with Priors", test)