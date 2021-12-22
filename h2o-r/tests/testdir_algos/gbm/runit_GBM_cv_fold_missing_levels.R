setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.GBM.fold.assignment.missing_levels <- function() {

    N <- 5
    skipped_fold <- 3
    full <- as.h2o(iris)
    fold <- h2o.kfold_column(full, nfolds = 5, seed = 1234)

    full$Fold <- as.factor(fold)
    skipped <- full[full$Fold != as.character(skipped_fold),]

    # Show the original levels were preserved
    expect_equal(c("0", "1", "2", "3", "4"), h2o.levels(skipped, "Fold"))

    m_skipped <- h2o.gbm(1:3, 4, skipped, fold_column="Fold",
                         seed=42)

    removed <- as.h2o(as.data.frame(skipped))
    # Show the original levels were not preserved
    expect_equal(c("0", "1", "2", "4"), h2o.levels(removed, "Fold"))
    m_removed <- h2o.gbm(1:3, 4, removed, fold_column="Fold",
                         seed=42)

    # CV models have to be the same
    skipped_MSEs <- c()
    removed_MSEs <- c()
    for (i in 1:(N-1)) {
      skipped_MSE <- h2o.performance(h2o.getModel(m_skipped@model$cross_validation_models[[i]]$name), train = TRUE)@metrics$MSE
      skipped_MSEs <- c(skipped_MSEs, skipped_MSE)
      removed_MSE <- h2o.performance(h2o.getModel(m_removed@model$cross_validation_models[[i]]$name), train = TRUE)@metrics$MSE
      removed_MSEs <- c(removed_MSEs, removed_MSE)
    }
    expect_equal(skipped_MSEs, removed_MSEs)
}

doTest("GBM Cross-Validation Fold Assignment with Missing Levels", test.GBM.fold.assignment.missing_levels)
