setwd(normalizePath(dirname(R.utils::commandArgs(asValues = TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
library(uplift)


test.uplift <- function() {
    ntrees <- 20
    seed <- 42
    uplift_metric <- "KL"
    auuc_type <- "qini"
    set.seed(seed)
    x <- c("X1", "X2", "X3", "X4", "X5", "X6")
    y <- "y"
    treatment_col <- "treat"

    # Test data preparation for each implementation
    train <- sim_pte(n = 2000, p = 6, rho = 0, sigma = sqrt(2), beta.den = 4)
    train$treat <- ifelse(train$treat == 1, 1, 0)
    test <- sim_pte(n = 1000, p = 6, rho = 0, sigma = sqrt(2), beta.den = 4)
    test$treat <- ifelse(test$treat == 1, 1, 0)

    trainh2o <- train
    trainh2o$treat <- as.factor(train$treat)
    trainh2o$y <- as.factor(train$y)
    trainh2o <- as.h2o(trainh2o)

    testh2o <- test
    testh2o$treat <- as.factor(test$treat)
    testh2o$y <- as.factor(test$y)
    testh2o <- as.h2o(testh2o)
    
    model <- h2o.upliftRandomForest(
        x = x,
        y = y,
        training_frame = trainh2o,
        validation_frame = testh2o,
        treatment_column = treatment_col,
        uplift_metric = uplift_metric,
        auuc_type = auuc_type,
        ntrees = ntrees,
        seed = seed)

    print(model)

    model_es <- h2o.upliftRandomForest(
        x = x,
        y = y,
        training_frame = trainh2o,
        validation_frame = testh2o,
        treatment_column = treatment_col,
        uplift_metric = uplift_metric,
        auuc_type = auuc_type,
        ntrees = ntrees,
        seed = seed,
        stopping_rounds=5, 
        stopping_metric="AUUC", 
        stopping_tolerance=0.1)

    print(model_es)

    num_trees <- model@model$model_summary$number_of_trees
    print("Number of trees built without AUUC early-stopping:")
    print(num_trees)
    num_trees_es <- model_es@model$model_summary$number_of_trees
    print("Number of trees built with AUUC early-stopping:")
    print(num_trees_es)
    expect_true(num_trees > num_trees_es)
    expect_true(num_trees > model_es@params$actual$ntrees)
    expect_true(num_trees_es == model_es@params$actual$ntrees)
    expect_true(ntrees == model_es@params$input$ntrees)
}

doTest("Uplift Random Forest Test: Test H2O RF uplift", test.uplift)
