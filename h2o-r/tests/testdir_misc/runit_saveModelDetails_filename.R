setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.saveModelDetails_filename <- function() {
  data <- as.h2o(iris)
  features <- c("Sepal.Length", "Sepal.Length", "Sepal.Width", "Petal.Length", "Petal.Width")
  model <- h2o.gbm(x=features, y = "Species", training_frame = data)

  # Default filename is model_id
  model_details_path <- h2o.saveModelDetails(model, path = tempdir())
  expect_true(endsWith(model_details_path, paste0(model@model_id, ".json")))
  expect_true(file.exists(model_details_path))

  # Specify filename
  model_details_path <- h2o.saveModelDetails(model, path = tempdir(), filename = "gbm_iris.json")
  expect_true(endsWith(model_details_path, "gbm_iris.json"))
  expect_true(file.exists(model_details_path))

  # Specify filename
  model_details_path <- h2o.saveModelDetails(model, path = tempdir(), filename = "gbm_iris")
  expect_true(endsWith(model_details_path, "gbm_iris"))
  expect_true(file.exists(model_details_path))
}

doTest("Test saveModelDetails with given filename", test.saveModelDetails_filename)
