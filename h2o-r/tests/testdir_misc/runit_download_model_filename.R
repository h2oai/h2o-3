setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.download_model_filename <- function() {
  data <- as.h2o(iris)
  features <- c("Sepal.Length", "Sepal.Length", "Sepal.Width", "Petal.Length", "Petal.Width")
  model <- h2o.gbm(x=features, y = "Species", training_frame = data)

  # Default filename is model_id
  model_path <- h2o.download_model(model, path = tempdir())
  expect_true(endsWith(model_path, model@model_id))
  model_path <- h2o.loadModel(model_path)
  expect_true(!is.null(model_path))

  # Specify filename
  model_path <- h2o.download_model(model, path = tempdir(), filename = "gbm_iris.model")
  expect_true(endsWith(model_path, "gbm_iris.model"))
  model_path <- h2o.loadModel(model_path)
  expect_true(!is.null(model_path))

  # Specify filename
  model_path <- h2o.download_model(model, path = tempdir(), filename = "gbm_iris")
  expect_true(endsWith(model_path, "gbm_iris"))
  model_path <- h2o.loadModel(model_path)
  expect_true(!is.null(model_path))

  # Wrong input
  error <- try(h2o.download_model(data, path = tempdir()))
  expect_true(class(error) == "try-error")
  error_type <- attr(error,"condition")
  expect_true(error_type$message == "`model` must be an H2OModel object")
}

doTest("Test download_model with given filename", test.download_model_filename)
