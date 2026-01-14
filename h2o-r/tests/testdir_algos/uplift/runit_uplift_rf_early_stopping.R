setwd(normalizePath(dirname(R.utils::commandArgs(asValues = TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


test.uplift <- function() {
    ntrees <- 42
    max_depth <- 2
    min_rows <- 10
    sample_rate <- 0.8
    seed <- 42
    set.seed(seed)
    x <- c("feature_1", "feature_2", "feature_3", "feature_4", "feature_5", "feature_6")
    y <- "outcome"
    treatment_col <- "treatment"

    # Test data preparation for each implementation
    train <- h2o.importFile(path=locate("smalldata/uplift/upliftml_train.csv"), 
                            col.types=list(by.col.name=c(treatment_col, y), types=c("factor", "factor")))
    test <- h2o.importFile(path=locate("smalldata/uplift/upliftml_test.csv"), 
                           col.types=list(by.col.name=c(treatment_col, y), types=c("factor", "factor")))
    
    model <- h2o.upliftRandomForest(
        x = x,
        y = y,
        training_frame = train,
        validation_frame = test,
        treatment_column = treatment_col,
        ntrees = ntrees,
        max_depth = max_depth,
        min_rows = min_rows,
        sample_rate = sample_rate,
        score_each_iteration=TRUE,
        seed = seed)

    print(model)

    model_es <- h2o.upliftRandomForest(
        x = x,
        y = y,
        training_frame = train,
        validation_frame = test,
        treatment_column = treatment_col,
        ntrees = ntrees,
        max_depth = max_depth,
        min_rows = min_rows,
        sample_rate = sample_rate,
        seed = seed,
        stopping_rounds=2, 
        stopping_metric="AUUC", 
        score_each_iteration=TRUE,
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
