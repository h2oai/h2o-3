setwd(normalizePath(dirname(R.utils::commandArgs(asValues = TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
library(uplift)


test.uplift <- function() {
    ntrees <- 10
    mtries <- 6
    seed <- 42
    uplift_metric <- "KL"
    set.seed(seed)

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
            x = c("X1", "X2", "X3", "X4", "X5", "X6"),
            y = "y",
            training_frame = trainh2o,
            validation_frame = testh2o,
            treatment_column = "treat",
            uplift_metric = uplift_metric,
            auuc_type = "qini",
            distribution = "bernoulli",
            ntrees = ntrees,
            mtries = mtries,
            max_depth = 10,
            min_rows = 10,
            nbins = 100,
            seed = seed)

    print(model)
    pred.uplift <- h2o.predict(model, testh2o)
    print(pred.uplift)

    tmpdir <- tempdir()
    print(tmpdir)
    modelfile <- h2o.download_mojo(model, path=tmpdir)
    print(modelfile)
    modelpath <- paste0(tmpdir, "/", modelfile)
    print(modelpath)
    model.mojo <- h2o.import_mojo(modelpath)
    pred.mojo <- h2o.predict(model.mojo, testh2o)
    print(pred.mojo)
}

doTest("Uplift Random Forest Test: Test H2O RF uplift", test.uplift)
