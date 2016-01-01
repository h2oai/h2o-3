setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
###############################################################
#### Test Order of input features for Beta Constraints  #######
###############################################################



test <- function() {
  ## Import data
  h2oData <- h2o.importFile("/mnt/0xcustomer-datasets/c27/data.csv")
  bc <- h2o.importFile("/mnt/0xcustomer-datasets/c27/constraints_indices.csv")
  bc <- bc[1:(nrow(bc)-1),] # remove intercept
  bc <- as.data.frame(bc)

  ## Set Parameters
  indVars <-  as.character(bc[1:nrow(bc), "names"])
  depVars <- "C3"
  totRealProb <- 0.002912744
  lambda <- 1e-8
  alpha <- 0
  family_type <- "binomial"

  ## Take subset of data
  h2oTest.logInfo("Subset dataset to only predictor and response variables...")
  h2oData <- h2oData[,c(indVars, depVars)]

  ## Run GLM
  run_glm <- function(data, beta_constraints){
    h2o.glm(x = indVars, y = depVars, training_frame = data, family = family_type, prior = totRealProb, lambda = lambda, alpha = alpha, beta_constraints = beta_constraints)
  }

  h2oTest.logInfo("Run GLM with original data and original constraints.")
  a <- run_glm(data = h2oData, beta_constraints = bc)

  h2oTest.logInfo("Run GLM with reordered data and original constraints.")
  b <- run_glm(data = h2oData, beta_constraints = bc)

  h2oTest.logInfo("Run GLM with reordered data and reordered beta constraints ")
  bc2 <- rbind(bc[6:nrow(bc),], bc[1:5,])
  c <- run_glm(data = h2oData, beta_constraints = bc2)

  checkEqualsNumeric(h2o.coef(a), h2o.coef(b))
  checkEqualsNumeric(h2o.coef(b), h2o.coef(c))
}

h2oTest.doTest("GLM Test: Beta Constraints with Priors", test)
