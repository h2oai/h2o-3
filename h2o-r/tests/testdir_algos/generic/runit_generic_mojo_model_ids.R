setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


test.model.generic.mojo.ids <- function() {
  data <- as.h2o(iris)
  features <- c("Sepal.Length", "Sepal.Length", "Sepal.Width", "Petal.Length", "Petal.Width")
    
  # Train MOJO  
  original_model <- h2o.gbm(x=features, y = "Species", training_frame = data)
  original_model_id <- original_model@model_id
  print(original_model_id)

  # Save MOJO  
  mojo_original_name <- h2o.save_mojo(original_model, path = tempdir())
  mojo_original_path <- paste0(mojo_original_name)
    
  # Import MOJO
  print("Import MOJO with a given model_id")
  mojo_model <- h2o.import_mojo(mojo_original_path, original_model_id)

  print(mojo_model@model_id)
  expect_equal(mojo_model@model_id, original_model_id)

  print("Import MOJO without a given model_id will auto-generate a new model_id with Generic prefix")
  mojo_model <- h2o.import_mojo(mojo_original_path)

  print(mojo_model@model_id)
  expect_true(startsWith(mojo_model@model_id, "Generic"))
  
  # MOJO Upload
  print("Upload MOJO with a given model_id")
  mojo_original_name <- h2o.download_mojo(model = original_model, path = tempdir())
  mojo_original_path <- paste0(tempdir(),"/",mojo_original_name)
  mojo_model_up <- h2o.upload_mojo(mojo_original_path, original_model_id)

  print(mojo_model_up@model_id)  
  expect_equal(mojo_model_up@model_id, original_model_id)
    
  print("Upload MOJO without a given model_id will auto-generate a new model_id with Generic prefix")
  mojo_model_up <- h2o.upload_mojo(mojo_original_path)

  print(mojo_model_up@model_id)
  expect_true(startsWith(mojo_model_up@model_id, "Generic"))
}

doTest("Generic model from GBM MOJO", test.model.generic.mojo.ids)
