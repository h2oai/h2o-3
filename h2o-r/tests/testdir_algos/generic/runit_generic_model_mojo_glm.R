source("generic_model_test_common.R")
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.model.generic.glm <- function() {
    
    # Regression
    data <- h2o.importFile(path = locate('smalldata/testng/airlines_train.csv'))
    cols <- c("Origin", "IsDepDelayed")
    original_model <- h2o.glm(x=cols, y = "Distance", training_frame = data, validation_frame = data, nfolds = 3)
    print(original_model)
    mojo_original_name <- h2o.download_mojo(model = original_model, path = tempdir())
    mojo_original_path <- paste0(tempdir(),"/",mojo_original_name)
    
    generic_model <- h2o.genericModel(mojo_original_path)
    print(generic_model)
    
    
    original_output <- capture.output(print(original_model))
    generic_output <- capture.output(print(generic_model))
    compare_output(original_output, generic_output,
                   c("Extract .+ frame","H2ORegressionModel: glm", "Model ID", "H2ORegressionMetrics: glm"),
                   c("H2ORegressionModel: generic", "Model ID", "H2ORegressionMetrics: generic"))
    
    generic_model_preds  <- h2o.predict(generic_model, data)
    expect_equal(length(generic_model_preds), 1)
    expect_equal(h2o.nrow(generic_model_preds), 24421)
    generic_model_path <- h2o.download_mojo(model = generic_model, path = tempdir())
    expect_equal(file.size(paste0(tempdir(),"/",generic_model_path)), file.size(mojo_original_path))
    
    # Binomial
    
    cols <- c("Origin", "Distance")
    original_model <- h2o.glm(x=cols, y = "IsDepDelayed", training_frame = data, validation_frame = data, nfolds = 3, family = "binomial")
    print(original_model)
    mojo_original_name <- h2o.download_mojo(model = original_model, path = tempdir())
    mojo_original_path <- paste0(tempdir(),"/",mojo_original_name)
    
    generic_model <- h2o.genericModel(mojo_original_path)
    print(generic_model)
    
    
    original_output <- capture.output(print(original_model))
    generic_output <- capture.output(print(generic_model))
    compare_output(original_output, generic_output,
                   c("Extract .+ frame","H2OBinomialModel: glm", "Model ID", "H2OBinomialMetrics: glm"),
                   c("H2OBinomialModel: generic", "Model ID", "H2OBinomialMetrics: generic"))
    
}

doTest("Generic model from GLM MOJO", test.model.generic.glm )
