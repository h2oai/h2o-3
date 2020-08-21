setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.model.generic.mojo.convenience<- function() {
  data <- as.h2o(iris)
  features <- c("Sepal.Length", "Sepal.Length", "Sepal.Width", "Petal.Length", "Petal.Width")
  original_model <- h2o.gbm(x=features, y = "Species", training_frame = data)
  mojo_original_name <- h2o.save_mojo(original_model, path = tempdir())
  mojo_original_path <- paste0(mojo_original_name)
  
  # Mojo Import
  mojo_model <- h2o.import_mojo(mojo_original_path)
  
  predictions  <- h2o.predict(mojo_model, data)
  expect_equal(length(predictions), 4)
  expect_equal(h2o.nrow(predictions), 150)
  
  # MOJO Upload
  
  mojo_original_name <- h2o.download_mojo(model = original_model, path = tempdir())
  mojo_original_path <- paste0(tempdir(),"/",mojo_original_name)
  mojo_model <- h2o.upload_mojo(mojo_original_path)
  predictions  <- h2o.predict(mojo_model, data)
  expect_equal(length(predictions), 4)
  expect_equal(h2o.nrow(predictions), 150)
}

doTest("Generic model from GBM MOJO", test.model.generic.mojo.convenience)
