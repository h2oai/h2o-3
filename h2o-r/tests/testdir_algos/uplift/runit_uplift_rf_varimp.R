setwd(normalizePath(dirname(R.utils::commandArgs(asValues = TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


test.uplift.varimp <- function() {
    ntrees <- 3
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

    varimp <- h2o.varimp(model)
    print(varimp)
    varimp.size <- length(varimp[[1]])
    expect_true(varimp.size > 0)
    expect_equal(varimp.size, length(x)) 
    h2o.varimp_plot(model)
}

doTest("Uplift Distributed Random Forest Test: Test H2O UpliftDRF varimp", test.uplift.varimp)
