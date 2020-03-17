source("generic_model_test_common.R")
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.model.generic.stackedensemble <- function() {
    train <- h2o.uploadFile(locate("smalldata/testng/higgs_train_5k.csv"))
    y <- "response"
    x <- setdiff(names(train), y)
    train[, y] <- as.factor(train[, y])
    nfolds <- 5
    
    gbm <- h2o.gbm(
        x = x,
        y = y,
        training_frame = train,
        distribution = "bernoulli",
        ntrees = 10,
        nfolds = nfolds,
        fold_assignment = "Modulo",
        keep_cross_validation_predictions = TRUE,
        seed = 1
    )
    
    randomforest <- h2o.randomForest(
        x = x,
        y = y,
        training_frame = train,
        ntrees = 5,
        nfolds = nfolds,
        fold_assignment = "Modulo",
        keep_cross_validation_predictions = TRUE,
        seed = 1
    )
    stackedensemble <- h2o.stackedEnsemble(
        x = x,
        y = y,
        training_frame = train,
        base_models = list(gbm@model_id, randomforest@model_id)
    )
    print(stackedensemble)
    mojo_original_name <- h2o.download_mojo(model = stackedensemble, path = tempdir())
    mojo_original_path <- paste0(tempdir(),"/",mojo_original_name)
    
    generic_model <- h2o.genericModel(mojo_original_path)
    print(generic_model)
    
    generic_model_preds  <- h2o.predict(generic_model, train)
    expect_equal(length(generic_model_preds), 3)
    expect_equal(h2o.nrow(generic_model_preds), 5000)
    generic_model_path <- h2o.download_mojo(model = generic_model, path = tempdir())
    expect_equal(file.size(paste0(tempdir(),"/",generic_model_path)), file.size(mojo_original_path))
    
}

doTest("Generic model from StackedEnsemble MOJO", test.model.generic.stackedensemble )
