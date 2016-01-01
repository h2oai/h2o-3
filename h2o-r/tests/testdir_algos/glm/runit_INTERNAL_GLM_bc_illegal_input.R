setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
###############################################################
###### Catch illegal input for GLM w/ Beta Constraints  #######
###############################################################

test <- function() {
  ## Import data
  h2oData <- h2o.importFile("/mnt/0xcustomer-datasets/c27/data.csv")
  betaConstraints <- h2o.importFile("/mnt/0xcustomer-datasets/c27/constraints_indices.csv")
  betaConstraints <- betaConstraints[1:(nrow(betaConstraints)-1),] # remove intercept
  bc <- as.data.frame(betaConstraints)

  ## Set Parameters
  indVars <-  as.character(bc[1:nrow(bc), "names"])
  depVars <- "C3"
  lambda <- 1e-8
  alpha <- 0
  family_type <- "binomial"

  ## Function to run GLM with specific beta_constraints
  run_glm <- function(bc) {
    h2o.glm(x = indVars, y = depVars, training_frame = h2oData, family = family_type,
            lambda = lambda, alpha = alpha, beta_constraints = bc)
  }

  h2oTest.logInfo("Illegal input case: Duplicate beta constraint entries.")
  a <- rbind(bc[1,],bc)
  checkException(run_glm(a), "Did not catch duplicate constraint.")

  h2oTest.logInfo("Illegal input case: No such predictor.")
  b <- data.frame(names = "fakeFeature", lower_bounds = -10000, upper_bounds = 10000, beta_given = 1, rho =1)
  b <-  rbind(bc, b)
  checkException(run_glm(b), "Did not catch nonexist feature named fakeFeature in the beta constraints data.frame.")

  #CNC - Tomas comments that an empty frame is fine, and should not throw an exception
  #h2oTest.logInfo("Illegal input case: Empty beta constraints frame.")
  #empty <- betaConstraints[betaConstraints$lower_bounds == 22,]
  #checkException(run_glm(empty), "Did not reject empty frame.", silent = T)

  h2oTest.logInfo("Illegal input case: Typo in beta constraint column name.")
  c <- bc
  names(c) <- gsub("lower_bounds", replacement = "lowerbounds", x = names(bc))
  checkException(run_glm(c), "Did not detect beta constraint column name typo.", silent = T)
}

h2oTest.doTest("GLM Test: Beta Constraints Illegal Argument Exceptions", test)
