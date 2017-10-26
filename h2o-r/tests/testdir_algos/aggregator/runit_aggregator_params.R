setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.aggregator.params.allValid <- function() {
  df <- h2o.uploadFile( locate("smalldata/airlines/allyears2k_headers.zip"))
  expect_that(
    h2o.aggregator(
      model_id="agg",
      training_frame=df,
      ignore_const_cols=FALSE,
      target_num_exemplars=500,
      rel_tol_num_exemplars=0.3,
      transform="STANDARDIZE",
      categorical_encoding="Eigen"
    ),
    not(throws_error()),
    "Setting valid aggregator parameters should not throw an error!"
  )
}

doTest("Aggregator Test: Setting all valid parameters", test.aggregator.params.allValid)
