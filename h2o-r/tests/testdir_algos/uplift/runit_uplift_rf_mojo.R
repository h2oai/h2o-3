setwd(normalizePath(dirname(R.utils::commandArgs(asValues = TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
library(uplift)


test.uplift <- function() {
    ntrees <- 6
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
    pred.uplift.df <- as.data.frame(pred.uplift)

    tmpdir <- tempdir()
    modelfile <- h2o.download_mojo(model, path=tmpdir)
    modelpath <- paste0(tmpdir, "/", modelfile)
    
    model.mojo <- h2o.import_mojo(modelpath)
    print(model.mojo)
    pred.mojo <- h2o.predict(model.mojo, testh2o)
    pred.mojo.df <- as.data.frame(pred.mojo)

    expect_equal(pred.mojo.df[1,1], pred.uplift.df[1,1])
    expect_equal(pred.mojo.df[2,1], pred.uplift.df[2,1])
    expect_equal(pred.mojo.df[10,1], pred.uplift.df[10,1])
    expect_equal(pred.mojo.df[42,1], pred.uplift.df[42,1])
    expect_equal(pred.mojo.df[550,1], pred.uplift.df[550,1])
    expect_equal(pred.mojo.df[666,1], pred.uplift.df[666,1])

    perf.uplift <- h2o.performance(model)
    print(perf.uplift)
    auuc.uplift <- h2o.auuc(perf.uplift)
    print(auuc.uplift)

    perf.mojo <- h2o.performance(model.mojo)
    print(perf.mojo)
    auuc.mojo <- h2o.auuc(perf.mojo)
    print(auuc.mojo)

    expect_equal(auuc.uplift, auuc.mojo)

    on.exit(unlink(modelpath,recursive=TRUE))
    on.exit(unlink(tmpdir,recursive=TRUE))
}

doTest("Uplift Random Forest Test: Test H2O RF uplift", test.uplift)
