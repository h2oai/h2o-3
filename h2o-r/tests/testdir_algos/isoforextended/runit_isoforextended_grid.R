setwd(normalizePath(dirname(
  R.utils::commandArgs(asValues = TRUE)$"f"
)))
source("../../../scripts/h2o-r-test-setup.R")


test.grid.extendedisolationforest <- function() {
  single_blob.hex <-
    h2o.importFile(path = locate("smalldata/anomaly/single_blob.csv"),
                   destination_frame = "single_blob.hex")

  ntrees <- c(25, 50, 100)
  sample_size <- c(64, 128, 256)
  extension_level <- c(0, 1)
  size_of_hyper_space <- length(ntrees)*length(sample_size)*length(extension_level)

  hyper_parameters <- list(ntrees = ntrees, sample_size = sample_size, extension_level = extension_level)
  baseline_grid <-
    h2o.grid(
      "extendedisolationforest",
      grid_id = "extisofor_grid_test",
      x = c(1, 2),
      training_frame = single_blob.hex,
      hyper_params = hyper_parameters,
      parallelism = 0
    )
  print(paste("Expected size of hyperparameter space is", length(baseline_grid@model_ids)))
  expect_equal(length(baseline_grid@model_ids), size_of_hyper_space)
}


doTest("Parallel Grid Search test for Extended Isolation Forest", test.grid.extendedisolationforest)
