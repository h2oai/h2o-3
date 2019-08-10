source("./h2o-r/tests/testdir_algos/generic/generic_model_test_common.R")
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.model.generic.drf <- function() {
    data <- h2o.importFile(path = locate('smalldata/testng/airlines_train.csv'))
    
    # Binomial
    cols <- c("Origin", "Distance")
    original_model <- h2o.randomForest(x=cols, y = "IsDepDelayed", training_frame = data, validation_frame = data, nfolds = 3)
    print(original_model)
    
    mojo_original_name <- h2o.download_mojo(model = original_model, path = tempdir())
    mojo_original_path <- paste0(tempdir(),"/",mojo_original_name)
    
    generic_model <- h2o.genericModel(mojo_original_path)
    print(generic_model)
    
    original_output <- capture.output(print(original_model))
    
    generic_output <- capture.output(print(generic_model))
    generic_output <- drop_model_parameters_from_printout(generic_output)
    
    compare_output(original_output, generic_output,
                   c("Extract .+ frame","H2OBinomialModel: drf", "Model ID", "H2OBinomialMetrics: drf"),
                   c("H2OBinomialModel: generic", "Model ID", "H2OBinomialMetrics: generic"))
    
    generic_model_preds  <- h2o.predict(generic_model, data)
    expect_equal(length(generic_model_preds), 3)
    expect_equal(h2o.nrow(generic_model_preds), 24421)
    generic_model_path <- h2o.download_mojo(model = generic_model, path = tempdir())
    expect_equal(file.size(paste0(tempdir(),"/",generic_model_path)), file.size(mojo_original_path))
    
    # Multinomial
    cols <- c("Origin", "Distance")
    original_model <- h2o.randomForest(x=cols, y = "Dest", training_frame = data, validation_frame = data, nfolds = 3)
    print(original_model)
    
    mojo_original_name <- h2o.download_mojo(model = original_model, path = tempdir())
    mojo_original_path <- paste0(tempdir(),"/",mojo_original_name)
    
    generic_model <- h2o.genericModel(mojo_original_path)
    print(generic_model)
    
    original_output <- capture.output(print(original_model))
    
    generic_output <- capture.output(print(generic_model))
    generic_output <- drop_model_parameters_from_printout(generic_output)
    
    compare_output(original_output, generic_output,
                   c("Extract .+ frame", "H2OMultinomialModel: drf", "Model ID", "H2OMultinomialMetrics: drf"),
                   c("H2OMultinomialModel: generic", "Model ID", "H2OMultinomialMetrics: generic"))
    
    # Regression
    cols <- c("Origin", "Dest")
    original_model <- h2o.randomForest(x=cols, y = "Distance", training_frame = data, validation_frame = data, nfolds = 3)
    print(original_model)
    
    mojo_original_name <- h2o.download_mojo(model = original_model, path = tempdir())
    mojo_original_path <- paste0(tempdir(),"/",mojo_original_name)
    
    generic_model <- h2o.genericModel(mojo_original_path)
    print(generic_model)
    
    original_output <- capture.output(print(original_model))
    
    generic_output <- capture.output(print(generic_model))
    generic_output <- drop_model_parameters_from_printout(generic_output)
    
    compare_output(original_output, generic_output,
                   c("Extract .+ frame","H2ORegressionModel: drf", "Model ID", "H2ORegressionMetrics: drf"),
                   c("H2ORegressionMetrics: generic", "Model ID", "H2ORegressionModel: generic"))
    
    
}

doTest("Generic model from DRF MOJO", test.model.generic.drf )
