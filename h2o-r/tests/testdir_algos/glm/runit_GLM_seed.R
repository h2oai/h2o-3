setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



check.glm.seed <- function() {
    # Import a sample binary outcome train/test set into R
    train <- h2o.uploadFile(locate("smalldata/junit/cars_20mpg.csv"))

    y <- "economy_20mpg"
    x <- setdiff(names(train), y)
    x <- setdiff(x,"economy")
    x <- setdiff(x,"name")
    family <- "binomial"

    #For binary classification, response should be a factor
    train[,y] <- as.factor(train[,y])


    # Train a GLM
    fit_h2oglm1 <- h2o.glm(x = x,
                          y = y,
                          training_frame  = train,
                          family = "binomial",
                          alpha  = 1.0,                    # Lasso
                          lambda_search = T,                      # searching for best value of Lambda
                          max_iterations  = 1000,
                          nfolds  = 5,
                          seed = 1234,
                          max_active_predictors = 200,lambda_min_ratio = 1e-4)

    fit_h2oglm2 <- h2o.glm(x = x,
                           y = y,
                           training_frame  = train,
                           family = "binomial",
                           alpha  = 1.0,                    # Lasso
                           lambda_search = T,                      # searching for best value of Lambda
                           max_iterations  = 1000,
                           nfolds  = 5,
                           seed = 1234,
                           max_active_predictors = 200,lambda_min_ratio = 1e-4)


    expect_equal(h2o.coef(fit_h2oglm1) , h2o.coef(fit_h2oglm2))

    # without a seed
    # Train a GLM
    fit_h2oglm3 <- h2o.glm(x = x,
                          y = y,
                          training_frame  = train,
                          family = "binomial",
                          alpha  = 1.0,                    # Lasso
                          lambda_search = T,                      # searching for best value of Lambda
                          max_iterations  = 1000,
                          nfolds  = 5,
                          max_active_predictors = 200,lambda_min_ratio = 1e-4)

    fit_h2oglm4 <- h2o.glm(x = x,
                           y = y,
                           training_frame  = train,
                           family = "binomial",
                           alpha  = 1.0,                    # Lasso
                           lambda_search = T,                      # searching for best value of Lambda
                           max_iterations  = 1000,
                           nfolds  = 5,
                           max_active_predictors = 200,lambda_min_ratio = 1e-4)
    diff_max = max(abs(h2o.coef(fit_h2oglm3) - h2o.coef(fit_h2oglm4)))
    print(diff_max)
    expect_true(diff_max > 0)
}

doTest("Checking GLM seed argument ", check.glm.seed)