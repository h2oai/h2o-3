setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

compare_output <- function(original_output, generic_output){
    removed_original <- c()
    for (i in 1:length(original_output)) {
        line <- original_output[i]
        
        if(grepl("Extract .+ frame", line)  || grepl("H2OAnomalyDetectionModel: isolationforest", line)){ # There are no frames to extract in generic
            removed_original <- append(removed_original, i)
        } else if(grepl("Model ID", line)){
            removed_original <- append(removed_original, i)
        } else if(grepl("H2OAnomalyDetectionMetrics: isolationforest", line)){
            removed_original <- append(removed_original, i)
        }
    }
    if(length(removed_original) > 0){
        original_output <- original_output[-removed_original]        
    }
    
    removed_generic <- c()
    for (i in 1:length(generic_output)) {
        line <- generic_output[i]
        
        if(grepl("H2OAnomalyDetectionModel: generic", line) || grepl("H2OAnomalyDetectionMetrics: generic", line) ){
            removed_generic <- append(removed_generic, i)
        } else if(grepl("Model ID", line)){
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

test.model.generic.irf <- function() {
    data <- h2o.importFile(path = locate('smalldata/testng/airlines_train.csv'))
    cols <- c("Origin", "Distance")
    original_model <- h2o.isolationForest(x=cols, training_frame = data)
    print(original_model)

    mojo_original_name <- h2o.download_mojo(model = original_model, path = tempdir())
    mojo_original_path <- paste0(tempdir(),"/",mojo_original_name)
    
    #Compare the output of original model and the generic model with imported MOJO inside
    original_output <- capture.output(print(original_model))
    generic_model <- h2o.genericModel(mojo_original_path)
    print(generic_model)
    
    generic_output <- capture.output(print(generic_model))
    compare_output(original_output, generic_output)
    generic_model_preds  <- h2o.predict(generic_model, data)
    expect_equal(length(generic_model_preds), 1)
    expect_equal(h2o.nrow(generic_model_preds), 24421)
    generic_model_path <- h2o.download_mojo(model = generic_model, path = tempdir())
    expect_equal(file.size(paste0(tempdir(),"/",generic_model_path)), file.size(mojo_original_path))
    
}

doTest("Generic model from IRF MOJO", test.model.generic.irf )
