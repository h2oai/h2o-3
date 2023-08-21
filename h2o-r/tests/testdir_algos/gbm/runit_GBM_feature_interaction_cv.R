setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

library(ggplot2)

test.feature_interaction_with_cv <- function() {
    diamonds <- ggplot2::diamonds
    diamonds$cut <- factor(diamonds$cut, ordered = FALSE)
    diamonds$color <- factor(diamonds$color, ordered = FALSE)
    diamonds$clarity <- factor(diamonds$clarity, ordered = FALSE)
    diamonds <- as.h2o(diamonds)
    diamonds$expensive <- h2o.asfactor(ifelse(diamonds$price == 5000, 1, 0))

    train <- diamonds
    train$fold <- h2o.kfold_column(data = train, nfolds = 3, seed = 123)
    
    params <- list( x = setdiff(names(diamonds), "expensive"), y = "expensive", fold_column = "fold", training_frame = as.name("train"), validation_frame = NULL, distribution = "bernoulli", learn_rate = 0.1, ntrees = 500, min_split_improvement = 1e-3, stopping_rounds = 3, stopping_tolerance = 0.001, seed = 456 )
    my_gbm <- do.call(what = "h2o.gbm", args = params)
    
    # feature interaction with main model
    print(h2o.feature_interaction(model = my_gbm))
    
    # feature interaction with cv model where tree depth = 0
    my_cv_gbm <- h2o.getModel(my_gbm@model$cross_validation_models[[1]]$name)
    fi <-h2o.feature_interaction(model = my_cv_gbm)
    expect_true(is.null(fi))
}

doTest("Test feature interaction with CV enabled", test.feature_interaction_with_cv)
