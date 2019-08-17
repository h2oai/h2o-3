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

    print("Model parameters:")
    if(!is.null(generic_model@parameters)){
        print(generic_model@parameters)
        expect_equal(length(generic_model@parameters), 50)
    }
}

doTest("Generic model from DRF MOJO has its parameters exposed", test.model.generic.drf )
