setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.model.generic.gbm <- function() {
    data <- h2o.importFile(path = locate('smalldata/testng/airlines_train.csv'))
    cols <- c("Origin", "Distance")
    original_model <- h2o.gbm(x=cols, y = "IsDepDelayed", training_frame = data)

    mojo_original_name <- h2o.download_mojo(model = original_model, path = tempdir())
    mojo_original_path <- paste0(tempdir(),"/",mojo_original_name)
    
    generic_model <- h2o.genericModel(mojo_original_path)
    generic_model_preds  <- h2o.predict(generic_model, data)
    expect_equal(length(generic_model_preds), 3)
    expect_equal(h2o.nrow(generic_model_preds), 24421)
    generic_model_path <- h2o.download_mojo(model = generic_model, path = tempdir())
    expect_equal(file.size(paste0(tempdir(),"/",generic_model_path)), file.size(mojo_original_path))
}

doTest("Generic model from GBM MOJO", test.model.generic.gbm )
