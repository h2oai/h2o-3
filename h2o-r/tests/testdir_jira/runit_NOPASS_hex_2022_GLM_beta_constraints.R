## This test is to check the beta contraint argument for GLM
## The test will import the prostate data set,
## runs glm with and without beta contraints which will be checked
## against glmnet's results.

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.GLM.betaConstraints <- function(conn){
  
    Log.info("Importing prostate dataset...")
    prostate.hex <- h2o.importFile(conn, system.file("extdata", "prostate.csv", package = "h2o"))

    Log.info("Run gaussian model once to grab starting values for betas...")
    myX <-  c("AGE","RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON")
    myY <- "CAPSULE"
    my_glm <- h2o.glm(x = myX, y = myY, training_frame = prostate.hex, family = "gaussian")

    Log.info("Create default beta constraints frame...")
    lowerbound <- rep(-1, times = length(myX))
    upperbound <- rep(1, times = length(myX))
    colnames <- my_glm@model$coefficients_table$Column[my_glm@model$coefficients_table$Column != "Intercept"]
    betaConstraints <- data.frame(names = colnames, lower_bounds = lowerbound, upper_bounds = upperbound)
    betaConstraints.hex <- as.h2o(conn, betaConstraints, key = "betaConstraints.hex")
    Log.info("Pull data frame into R to run GLMnet...")
    DataFrame <- as.data.frame(prostate.hex)

    ########### run_glm function to run glm over different parameters
    ########### we want to vary family, alpha, standardization, beta constraint bounds
    run_glm <- function(  family_type = "gaussian",
                        alpha = 0.5,
                        standardization = T
                        ) {

    prostate.hex <- h2o.importFile(conn, system.file("extdata", "prostate.csv", package = "h2o"))
    Log.info(paste("Run H2O's GLM with :", "family =", family_type, ", alpha =", alpha, ",
                   standardization =", standardization, "..."))
    if(family_type == "binomial"){
        prostate.hex$CAPSULE <- as.factor(prostate.hex$CAPSULE)
    }
    glm_constraints.h2o <- h2o.glm(x = myX, y = myY, training_frame = prostate.hex, standardize = standardization,
                                   family = family_type, alpha = alpha , beta_constraint = betaConstraints.hex)

    Log.info("Run GLMnet with the same parameters, using lambda = 1e-05")
    glm_constraints.r <- glmnet(x = as.matrix(DataFrame[,myX]), alpha = alpha, lambda = 1e-05,
                                standardize = standardization, y = DataFrame[,myY], family = family_type,
                                lower.limits = lowerbound, upper.limits = upperbound, intercept=FALSE)

    checkEqualsNumeric(glm_constraints.h2o@model$coefficients_table$Coefficients[1:length(lowerbound)],
                       glm_constraints.r$beta[,1],
                       tolerance = 0.1)
    checkEqualsNumeric(glm_constraints.h2o@model$null_deviance,
                       glm_constraints.r$nulldev,
                       tolerance = 0.05)
    }

    families <- c("gaussian", "binomial", "poisson")
    alpha <- c(0,0.5,1.0)
    standard <- c(T, F)

    grid <- expand.grid(families, alpha, standard)
    names(grid) <- c("Family", "Alpha", "Standardize")

    fullTest <- mapply(run_glm, as.character(grid[,1]), grid[,2], grid[,3])
    testResults <- cbind(grid,Passed = fullTest)
    print(testResults)

    testEnd()
}

doTest("GLM Test: GLM w/ Beta Constraints", test.GLM.betaConstraints)

