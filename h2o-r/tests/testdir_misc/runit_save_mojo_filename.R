setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.save_mojo_filename <- function() {
  data <- as.h2o(iris)
  features <- c("Sepal.Length", "Sepal.Length", "Sepal.Width", "Petal.Length", "Petal.Width")
  model <- h2o.gbm(x=features, y = "Species", training_frame = data)

  # Default filename is model_id
  mojo_path <- h2o.save_mojo(model, path = tempdir())
  expect_true(endsWith(mojo_path, paste0(model@model_id, ".zip")))
  mojo_path <- h2o.import_mojo(mojo_path)
  expect_true(!is.null(mojo_path))

  # Specify filename
  mojo_path <- h2o.save_mojo(model, path = tempdir(), filename = "gbm_iris.zip")
  expect_true(endsWith(mojo_path, "gbm_iris.zip"))
  mojo_path <- h2o.import_mojo(mojo_path)
  expect_true(!is.null(mojo_path))

  # Specify filename
  mojo_path <- h2o.save_mojo(model, path = tempdir(), filename = "gbm_iris")
  expect_true(endsWith(mojo_path, "gbm_iris"))
  mojo_path <- h2o.import_mojo(mojo_path)
  expect_true(!is.null(mojo_path))
}

doTest("Test save_mojo with given filename", test.save_mojo_filename)
