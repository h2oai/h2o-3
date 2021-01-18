setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.gbm.effective_parameters <- function() {
    cars <- h2o.uploadFile(locate("smalldata/junit/cars_20mpg.csv"))
    seed <- sample(1:1000000, 1)
    r <- h2o.runif(cars,seed=seed)
    train <- cars[r > 0.2,]
    valid <- cars[r <= 0.2,]
    predictors <- c("displacement","power","weight","acceleration","year")
    response_col <- "economy_20mpg"
    train[,response_col] <- as.factor(train[response_col])
    valid[,response_col] <- as.factor(valid[response_col])
    
    model1 <- h2o.gbm(seed=1234, stopping_rounds=3, score_tree_interval=5, x=predictors,y=response_col,training_frame=train,validation_frame=valid)
    model2 <- h2o.gbm(seed=1234, stopping_rounds=3, score_tree_interval=5, x=predictors,y=response_col,training_frame=train,validation_frame=valid, distribution="bernoulli", stopping_metric="logloss", histogram_type="UniformAdaptive",
    categorical_encoding="Enum")
    
    
    expect_equal(model1@parameters$distribution, model2@allparameters$distribution)
    expect_equal(model1@parameters$stopping_metric, model2@allparameters$stopping_metric)
    expect_equal(model1@parameters$histogram_type, model2@allparameters$histogram_type) 
    expect_equal(model1@parameters$categorical_encoding, model2@allparameters$categorical_encoding)
}

doTest("Testing model accessors for GBM", test.gbm.effective_parameters)
