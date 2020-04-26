setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.saveMojo <- function() {

  data <- as.h2o(iris)
  features <- c("Sepal.Length", "Sepal.Length", "Sepal.Width", "Petal.Length", "Petal.Width")
  model <- h2o.gbm(x=features, y = "Species", training_frame = data)
  mojo_path <- h2o.saveMojo(model, path = tempdir()) # saveMojo should delegate to save_mojo
  mojo_model <- h2o.import_mojo(mojo_path)
  expect_true(!is.null(mojo_model)) # mojo should be importable back into H2O
  
}

doTest("Test delegation of deprecated saveMojo method call to save_mojo", test.saveMojo)
