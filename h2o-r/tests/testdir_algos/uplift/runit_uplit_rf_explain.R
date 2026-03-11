setwd(normalizePath(dirname(R.utils::commandArgs(asValues = TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


test.uplift <- function() {
    ntrees <- 2
    max_depth <- 2
    min_rows <- 10
    sample_rate <- 0.8
    seed <- 42
    set.seed(seed)
    x <- c("feature_1", "feature_2", "feature_3")
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
    expect_error(h2o.explain(model, test))
}

doTest("Uplift Random Forest Test: Test H2O RF uplift", test.uplift)
