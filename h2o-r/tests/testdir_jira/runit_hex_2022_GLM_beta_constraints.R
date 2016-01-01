setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
## This test is to check the beta contraint argument for GLM
## The test will import the prostate data set,
## runs glm with and without beta contraints which will be checked
## against glmnet's results.




test.GLM.betaConstraints <- function(){

    h2oTest.logInfo("Importing prostate dataset...")
    prostate.hex <- h2o.importFile(h2oTest.locate("smalldata/prostate/prostate.csv"))

    h2oTest.logInfo("Run gaussian model once to grab starting values for betas...")
    myX <-  c("AGE","RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON")
    myY <- "CAPSULE"
    my_glm <- h2o.glm(x = myX, y = myY, training_frame = prostate.hex, family = "gaussian")

    h2oTest.logInfo("Create default beta constraints frame...")
    lowerbound <- rep(-1, times = length(myX))
    upperbound <- rep(1, times = length(myX))
    colnames <- my_glm@model$coefficients_table$names[my_glm@model$coefficients_table$names != "Intercept"]
    betaConstraints <- data.frame(names = colnames, lower_bounds = lowerbound, upper_bounds = upperbound)
    betaConstraints.hex <- as.h2o(betaConstraints, destination_frame = "betaConstraints.hex")
    h2oTest.logInfo("Pull data frame into R to run GLMnet...")
    prostate.r <- as.data.frame(prostate.hex)

    ########### run_glm function to run glm over different parameters
    ########### we want to vary family, alpha, standardization, beta constraint bounds
    run_glm <- function(  family_type = "gaussian",
                        alpha = 0.5,
                        standardization = T
                        ) {
        prostate.hex <- h2o.importFile(h2oTest.locate("smalldata/prostate/prostate.csv"))
        h2oTest.logInfo(paste("Run H2O's GLM with :", "family =", family_type, ", alpha =", alpha, ",
                       standardization =", standardization, "..."))
        if(family_type == "binomial"){
            h2oTest.logInfo("family binomial chosen, so converting CAPSULE to factor")
            prostate.hex$CAPSULE <- as.factor(prostate.hex$CAPSULE)
        }

        glm_constraints.h2o <- h2o.glm(x = myX, y = myY, training_frame = prostate.hex, standardize = standardization,
                                   family = family_type, alpha = alpha , beta_constraint = betaConstraints.hex,
                                   lambda = 1e-05)

        h2oTest.logInfo("Run GLMnet with the same parameters, using lambda = 1e-05")
        glm_constraints.r <- glmnet(x = as.matrix(prostate.r[,myX]), alpha = alpha, lambda = 1e-05,
                                    standardize = standardization, y = prostate.r[,myY], family = family_type,
                                    lower.limits = lowerbound, upper.limits = upperbound, intercept=TRUE)

        # Check coefficients
        print("H2O betas:")
        print(glm_constraints.h2o@model$coefficients)
        print("Glmnet betas:")
        print(glm_constraints.r$beta[,1])
        print(" ")
        checkEqualsNumeric(glm_constraints.h2o@model$coefficients[2:8],
                           glm_constraints.r$beta[,1],
                           tolerance = 0.1)
        checkTrue(all(abs(glm_constraints.h2o@model$coefficients[2:8]) <= 1.0))

        # Check null deviances
        print("H2O null deviance:")
        print(glm_constraints.h2o@model$training_metrics@metrics$null_deviance)
        print("Glmnet null deviance:")
        print(glm_constraints.r$nulldev)
        checkEqualsNumeric(glm_constraints.h2o@model$training_metrics@metrics$null_deviance,
                           glm_constraints.r$nulldev,
                           tolerance = 0.05)

        # Check residual deviances
        print("H2O resid deviance:")
        print(glm_constraints.h2o@model$training_metrics@metrics$residual_deviance)
        print("Glmnet resid deviance:")
        glm_constraints_resid <- glm_constraints.r$nulldev*(1-glm_constraints.r$dev.ratio)
        print(glm_constraints_resid)
        checkEqualsNumeric(glm_constraints.h2o@model$training_metrics@metrics$residual_deviance,
                           glm_constraints_resid,
                           tolerance = 0.05)

        # Check objective values (gaussian)
        if(family_type == "gaussian"){
            h2o_obj_val <- (glm_constraints.h2o@model$training_metrics@metrics$residual_deviance / nrow(prostate.hex)) + (1 - alpha) * 1e-05 *
                            (t(glm_constraints.h2o@model$coefficients[1:7]) %*%
                            glm_constraints.h2o@model$coefficients[1:7]) + alpha * 1e-05 *
                            sum(abs(glm_constraints.h2o@model$coefficients[1:7]))
            glmnet_obj_val <- (glm_constraints_resid / nrow(prostate.hex)) + (1 - alpha) * 1e-05 *
                               (t(glm_constraints.r$beta[,1]) %*%
                               glm_constraints.r$beta[,1]) + alpha * 1e-05 *
                               sum(abs(glm_constraints.r$beta[,1]))
            print("H2O objective value:")
            print(h2o_obj_val)
            print("Glmnet objective value:")
            print(glmnet_obj_val)
            checkEqualsNumeric(h2o_obj_val,
                               glmnet_obj_val,
                               tolerance = 1e-04)
        }
    }

    families <- c("gaussian", "binomial", "poisson")
    alpha <- c(0,0.5,1.0)
    standard <- c(T, F)

    grid <- expand.grid(families, alpha, standard)
    names(grid) <- c("Family", "Alpha", "Standardize")

    fullTest <- mapply(run_glm, as.character(grid[,1]), grid[,2], grid[,3])

    
}

h2oTest.doTest("GLM Test: GLM w/ Beta Constraints", test.GLM.betaConstraints)

