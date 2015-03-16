## This test is to check the beta contraint argument for GLM
## The test will import the prostate data set,
## runs glm with and without beta contraints which will be checked
## against glmnet's results.

#setwd("/Users/amy/h2o/R/tests/testdir_jira")
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../findNSourceUtils.R')

test.GLM.betaConstraints <- function(conn) {
  
  Log.info("Importing prostate dataset...")
  prostate.hex = h2o.importFile(
    object = conn,system.file("extdata", "prostate.csv", package = "h2o"))
  
  Log.info("Run gaussian model once to grab starting values for betas...")
  myX =  c("AGE","RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON")
  myY = "CAPSULE"
  my_glm = h2o.glm(x = myX, y = myY, data = prostate.hex, family = "gaussian")  
  
  Log.info("Create default beta constraints frame...")
  lowerbound = rep(-1, times = length(myX)+1)
  upperbound = rep(1, times = length(myX)+1)
  starting = as.numeric(my_glm@model$coefficients)
  colnames = names(my_glm@model$coefficients)
  betaConstraints = data.frame(names = colnames, lower_bounds = lowerbound, upper_bounds = upperbound, beta_given= starting, rho = 0)
  betaConstraints.hex = as.h2o(conn, betaConstraints, key = "betaConstraints.hex")  
  Log.info("Pull data frame into R to run GLMnet...")
  prostate.csv = as.data.frame(prostate.hex)
  Log.info("Prep Data Frame for run in GLMnet, includes categorical expansions...")
  xDataFrame = cbind(prostate.csv[,myX], rep(0, times = nrow(prostate.hex)))
  names(xDataFrame) = c(myX, "Intercept")
  
  
  ########### run_glm function to run glm over different parameters
  ########### we want to vary family, alpha, standardization, beta constraint bounds  
  run_glm <- function(  family_type = "gaussian",
                        alpha = 0.5,
                        standardization = T,
                        bounds = c(-1,1)
                        ) {
    upper_bound = bounds[2]
    lower_bound = bounds[1]
    Log.info(paste("Set Beta Constraints :", "lower bound =", lower_bound,"and upper bound =", upper_bound, "..."))
    betaConstraints.hex = as.h2o(conn, betaConstraints, key = "betaConstraints.hex")
    betaConstraints.hex$upper_bounds = upper_bound
    betaConstraints.hex$lower_bounds = lower_bound
    
    Log.info(paste("Run H2O's GLM with :", "family =", family_type, ", alpha =", alpha, ", standardization =", standardization, "..."))
    glm_constraints.h2o = h2o.glm(x = myX, y = myY, data = prostate.hex, standardize = standardization, higher_accuracy = T,
                                  family = family_type, alpha = alpha , beta_constraints = betaConstraints.hex)
    lambda = glm_constraints.h2o@model$lambda
    
    Log.info(paste("Run GLMnet with the same parameters, using lambda =", lambda))
    glm_constraints.r = glmnet(x = as.matrix(xDataFrame), alpha = alpha, lambda = lambda, standardize = standardization,
                               y = prostate.csv[,myY], family = family_type, lower.limits = lower_bound, upper.limits = upper_bound)
    checkGLMModel2(glm_constraints.h2o, glm_constraints.r)
  }
  
  families = c("gaussian", "binomial", "poisson")
  alpha = c(0,0.5,1.0)
  standard = c(T, F)

  grid = expand.grid(families, alpha, standard)
  names(grid) = c("Family", "Alpha", "Standardize")
  
  fullTest <- mapply(run_glm, as.character(grid[,1]), grid[,2], grid[,3])  
  testResults <- cbind(grid,Passed = fullTest)
  print(testResults)  
  testEnd()
}

doTest("GLM Test: GLM w/ Beta Constraints", test.GLM.betaConstraints)

