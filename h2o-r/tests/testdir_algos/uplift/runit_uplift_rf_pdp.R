setwd(normalizePath(dirname(R.utils::commandArgs(asValues = TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


test.uplift.pdp <- function() {
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

    print(model)
    
    cols <- c("feature_3", "feature_4", "feature_5")
    cols.len <- length(cols)
    
    # Plot Partial dependence for test data
    pdp <- h2o.partialPlot(model, test, cols, plot=FALSE)
    expect_true(length(pdp) > 0)
    expect_true(length(pdp) == cols.len)
    
    mask <- test[,treatment_col] == "treatment"
    
    # Partial dependence plot for treatment group test data
    test_tr <- test[mask, ]
    pdp_tr <- h2o.partialPlot(model, test_tr, cols, plot=FALSE)
    expect_true(length(pdp_tr) > 0)
    expect_true(length(pdp_tr) == cols.len)
    
    # Partial dependence plot for control group test data    
    test_ct <- test[!mask, ]
    pdp_ct <- h2o.partialPlot(model, test_ct, cols, plot=FALSE)
    expect_true(length(pdp_ct) > 0)
    expect_true(length(pdp_ct) == cols.len)
}

doTest("Uplift Distributed Random Forest Test: Test H2O DRF Uplift PDP", test.uplift.pdp)
