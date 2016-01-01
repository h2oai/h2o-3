setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
## This test is to check the beta contraint argument for GLM
## The test will import the prostate data set,
## runs glm with and without beta contraints which will be checked
## against glmnet's results.




test <- function() {
  h2oTest.logInfo("Importing prostate dataset...")
  h2o_data <- h2o.importFile(h2oTest.locate("smalldata/prostate/prostate.csv"))

  myX <-  c("AGE","RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON")
  myY <- "CAPSULE"
  h2oTest.logInfo("Create default beta constraints frame...")
  lowerbound <- rep(-1, times = length(myX))
  upperbound <- rep(1, times = length(myX))
  r_bc <- data.frame(names = myX, lower_bounds = lowerbound, upper_bounds = upperbound)
  h2o_bc <- as.h2o(r_bc)

  h2oTest.logInfo("Pull data frame into R to run GLMnet...")
  r_data <- as.data.frame(h2o_data)

  ########### run_glm function to run glm over different parameters
  ########### we want to vary family, alpha, standardization, beta constraint bounds
  run_glm <- function(  family_type = "gaussian",
                        alpha = 0.5,
                        standardization = T,
                        lower_bound,
                        upper_bound
                        ) {
    h2oTest.logInfo(paste("Set Beta Constraints :", "lower bound =", lower_bound,"and upper bound =", upper_bound, "..."))
    h2o_bc <- as.h2o(r_bc)
    h2o_bc$upper_bounds <- upper_bound
    h2o_bc$lower_bounds <- lower_bound

    h2oTest.logInfo(paste("Run H2O's GLM with :", "family =", family_type, ", alpha =", alpha, ", standardization =",
                   standardization, "..."))
    h2o_glm <- h2o.glm(x = myX, y = myY, training_frame = h2o_data, standardize = standardization,
                       family = family_type, alpha = alpha , beta_constraints = h2o_bc)
    lambda <- h2o_glm@allparameters$lambda

    h2oTest.logInfo(paste("Run GLMnet with the same parameters, using lambda =", lambda))
    r_glm <- glmnet(x = as.matrix(r_data[,myX]), alpha = alpha, lambda = lambda, standardize = standardization,
                    y = r_data[,myY], family = family_type, lower.limits = lower_bound, upper.limits = upper_bound)
    h2oTest.checkGLMModel2(h2o_glm, r_glm)
  }

  families <- c("gaussian")
  alpha <- c(0,0.5,1.0)
  standard <- c(T, F)

  grid <- expand.grid(families, alpha, standard)
  names(grid) <- c("Family", "Alpha", "Standardize")

  b <- mapply(run_glm, as.character(grid[,1]), grid[,2], grid[,3], rep(-1,nrow(grid)), rep(0, nrow(grid)))
  t <- cbind(grid,Passed = b)
  print("TEST RESULTS FOR PROSTATE DATA SET with bounds [-1,0] : ")
  print(t)

  c <- mapply(run_glm, as.character(grid[,1]), grid[,2], grid[,3], rep(0,nrow(grid)), rep(1, nrow(grid)))
  t <- cbind(grid,Passed = c)
  print("TEST RESULTS FOR PROSTATE DATA SET with bounds [0,1] : ")
  print(t)

  d <- mapply(run_glm, as.character(grid[,1]), grid[,2], grid[,3], rep(-0.1,nrow(grid)), rep(0.1, nrow(grid)))
  t <- cbind(grid,Passed = d)
  print("TEST RESULTS FOR PROSTATE DATA SET with bounds [-0.1,0.1] : ")
  print(t)

  
}

h2oTest.doTest("GLM Test: GLM w/ Beta Constraints", test)
