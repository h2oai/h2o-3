setwd(normalizePath(dirname(R.utils::commandArgs(asValues = TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pubdev.5323 <- function() {
    train <- h2o.importFile(locate("smalldata/higgs/higgs_train_10k.csv"))
    test <- h2o.importFile(locate("smalldata/higgs/higgs_test_5k.csv"))

    y <- "response"
    x <- setdiff(names(train), y)

    train[,y] <- as.factor(train[,y])
    test[,y] <- as.factor(test[,y])

    # Train a bunch of GLM models
    .wrapper <- function(alpha) {
        h2o.glm(training_frame = train, family = "binomial", alpha = alpha, x = x, y = y, seed = 1, nfolds = 3,
                keep_cross_validation_predictions = TRUE,
                lambda_search = TRUE,
                balance_classes = TRUE,
                early_stopping = TRUE)
    }
    models <- sapply(X = seq(0, 0.10, 0.05), FUN = .wrapper)

    # Build the ensemble from pre-trained models
    ensemble <- h2o.stackedEnsemble(x = x, y = y, training_frame = train, base_models = models)
    # It should not fail and return the ensemble
    expect_false(is.null(ensemble))
}

doTest("PUBDEV-5323: Stacked Ensemble fails when using a grid or list of GLMs as base models", test.pubdev.5323)
