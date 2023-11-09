source("generic_model_test_common.R")
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
library(uplift)


test.model.generic.drf <- function() {
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

    original_model <- h2o.upliftRandomForest(
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

    print(original_model)

    mojo_original_name <- h2o.download_mojo(model = original_model, path = tempdir())
    mojo_original_path <- paste0(tempdir(),"/",mojo_original_name)

    generic_model <- h2o.genericModel(mojo_original_path)
    print(generic_model)

    original_output <- capture.output(print(original_model))
    generic_output <- capture.output(print(generic_model))
    compare_output(original_output, generic_output,
                   c("Extract .+ frame","H2OBinomialUpliftModel: upliftdrf", "Model ID", "H2OBinomialUpliftMetrics: upliftdrf"),
                   c("H2OBinomialUpliftModel: generic", "Model ID", "H2OBinomialUpliftMetrics: generic"))

    generic_model_preds  <- h2o.predict(generic_model, testh2o)
    expect_equal(length(generic_model_preds), 3)
    expect_equal(h2o.nrow(generic_model_preds), 1000)
    generic_model_path <- h2o.download_mojo(model = generic_model, path = tempdir())
    expect_equal(file.size(paste0(tempdir(),"/",generic_model_path)), file.size(mojo_original_path))
}

doTest("Generic model from DRF MOJO", test.model.generic.drf )
