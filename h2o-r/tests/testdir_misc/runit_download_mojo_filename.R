setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.download_mojo_filename <- function() {
  data <- as.h2o(iris)
  features <- c("Sepal.Length", "Sepal.Length", "Sepal.Width", "Petal.Length", "Petal.Width")
  model <- h2o.gbm(x=features, y = "Species", training_frame = data)

  # Default filename is model_id
  mojo_path <- h2o.download_mojo(model, path = tempdir())
  expect_true((paste0(model@model_id, ".zip") == mojo_path))
  mojo_path <- h2o.import_mojo(file.path(tempdir(), mojo_path))
  expect_true(!is.null(mojo_path))

  # Specify filename
  mojo_path <- h2o.download_mojo(model, path = tempdir(), filename = "gbm_iris.zip")
  expect_true("gbm_iris.zip" == mojo_path)
  mojo_path <- h2o.import_mojo(file.path(tempdir(), "gbm_iris.zip"))
  expect_true(!is.null(mojo_path))

  # Specify filename
  mojo_path <- h2o.download_mojo(model, path = tempdir(), filename = "gbm_iris")
  expect_true("gbm_iris" == mojo_path)
  mojo_path <- h2o.import_mojo(file.path(tempdir(), "gbm_iris"))
  expect_true(!is.null(mojo_path))
}

doTest("Test download_mojo with given filename", test.download_mojo_filename)
