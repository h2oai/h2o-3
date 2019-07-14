setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

compare_output <- function(original_output, generic_output){

    removed_original <- c()
    for (i in 1:length(original_output)) {
        line <- original_output[i]
        
        if(grepl("Extract .+ frame", line)  || grepl("H2ORegressionModel: gbm", line) || grepl("H2OMultinomialModel: gbm", line)){ # There are no frames to extract in generic
            removed_original <- append(removed_original, i)
        } else if(grepl("H2OBinomialModel: gbm", line)){
            removed_original <- append(removed_original, i)
        } else if(grepl("Model ID", line)){
            removed_original <- append(removed_original, i)
        } else if(grepl("H2OBinomialMetrics: gbm", line)  || grepl("H2ORegressionMetrics: gbm", line) || grepl("H2OMultinomialMetrics: gbm", line)){
            removed_original <- append(removed_original, i)
        }
    }
    if(length(removed_original) > 0){
        original_output <- original_output[-removed_original]        
    }
    
    removed_generic <- c()
    for (i in 1:length(generic_output)) {
        line <- generic_output[i]
        
        if(grepl("H2OBinomialModel: generic", line) || grepl("H2ORegressionMetrics: generic", line) || grepl("H2OMultinomialModel: generic", line)){
            removed_generic <- append(removed_generic, i)
        } else if(grepl("Model ID", line)){
            removed_generic <- append(removed_generic, i)
        } else if(grepl("H2OBinomialMetrics: generic", line) || grepl("H2ORegressionModel: generic", line) || grepl("H2OMultinomialMetrics: generic", line)){
            removed_generic <- append(removed_generic, i)
        }
    }
    if(length(removed_generic) > 0){
        generic_output <- generic_output[-removed_generic]
    }
    print(original_output)
    print(generic_output)
    expect_equal(TRUE, all.equal(original_output, generic_output))
}


test.model.generic.gbm <- function() {
    data <- h2o.importFile(path = locate('smalldata/testng/airlines_train.csv'))
    cols <- c("Origin", "Distance")
    original_model <- h2o.gbm(x=cols, y = "IsDepDelayed", training_frame = data, validation_frame = data, nfolds = 3, ntrees = 1)
    print(original_model)
    mojo_original_name <- h2o.download_mojo(model = original_model, path = tempdir())
    mojo_original_path <- paste0(tempdir(),"/",mojo_original_name)
    
    generic_model <- h2o.genericModel(mojo_original_path)
    print(generic_model)
    
    original_output <- capture.output(print(original_model))
    generic_output <- capture.output(print(generic_model))
    compare_output(original_output, generic_output)
    
    generic_model_preds  <- h2o.predict(generic_model, data)
    expect_equal(length(generic_model_preds), 3)
    expect_equal(h2o.nrow(generic_model_preds), 24421)
    generic_model_path <- h2o.download_mojo(model = generic_model, path = tempdir())
    expect_equal(file.size(paste0(tempdir(),"/",generic_model_path)), file.size(mojo_original_path))
    
    # Regression
    cols <- c("Origin", "IsDepDelayed")
    original_model <- h2o.gbm(x=cols, y = "Distance", training_frame = data, validation_frame = data, nfolds = 3, ntrees = 1)
    print(original_model)
    mojo_original_name <- h2o.download_mojo(model = original_model, path = tempdir())
    mojo_original_path <- paste0(tempdir(),"/",mojo_original_name)
    
    generic_model <- h2o.genericModel(mojo_original_path)
    print(generic_model)
    
    original_output <- capture.output(print(original_model))
    generic_output <- capture.output(print(generic_model))
    compare_output(original_output, generic_output)
    
    # Multinomial
    cols <- c("Origin", "Distance")
    original_model <- h2o.gbm(x=cols, y = "Dest", training_frame = data, validation_frame = data, nfolds = 3, ntrees = 1)
    print(original_model)
    mojo_original_name <- h2o.download_mojo(model = original_model, path = tempdir())
    mojo_original_path <- paste0(tempdir(),"/",mojo_original_name)
    
    generic_model <- h2o.genericModel(mojo_original_path)
    print(generic_model)
    
    original_output <- capture.output(print(original_model))
    generic_output <- capture.output(print(generic_model))
    compare_output(original_output, generic_output)
    
}

doTest("Generic model from GBM MOJO", test.model.generic.gbm )
