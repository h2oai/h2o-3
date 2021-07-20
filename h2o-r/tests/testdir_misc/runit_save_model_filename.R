setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.save_model_filename <- function() {
  data <- as.h2o(iris)
  features <- c("Sepal.Length", "Sepal.Length", "Sepal.Width", "Petal.Length", "Petal.Width")
  model <- h2o.gbm(x=features, y = "Species", training_frame = data)

  # Default filename is model_id
  model_path <- h2o.saveModel(model, path = tempdir())
  expect_true(endsWith(model_path, model@model_id))
  model_path <- h2o.loadModel(model_path)
  expect_true(!is.null(model_path))

  # Specify filename
  model_path <- h2o.saveModel(model, path = tempdir(), filename = "gbm_iris.model")
  expect_true(endsWith(model_path, "gbm_iris.model"))
  model_path <- h2o.loadModel(model_path)
  expect_true(!is.null(model_path))

  # Specify filename
  model_path <- h2o.saveModel(model, path = tempdir(), filename = "gbm_iris")
  expect_true(endsWith(model_path, "gbm_iris"))
  model_path <- h2o.loadModel(model_path)
  expect_true(!is.null(model_path))
}

doTest("Test save_model with given filename", test.save_model_filename)
