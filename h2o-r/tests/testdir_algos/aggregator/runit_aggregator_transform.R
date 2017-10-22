setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.aggregator.params.transform <- function() {
  valid_values <- c("NONE", "STANDARDIZE", "NORMALIZE", "DEMEAN", "DESCALE")
  df <- h2o.createFrame(
    rows=100,
    cols=5,
    categorical_fraction=0.6,
    integer_fraction=0,
    binary_fraction=0,
    real_range=100,
    integer_range=100,
    missing_fraction=0,
    seed=123
  )

  for (val in valid_values) {
    expect_that(
      h2o.aggregator(training_frame=df, transform=val),
      not(throws_error()),
      "Valid transform parameter should not throw an error!"
    )
  }

  expect_error(h2o.aggregator(training_frame=df, transform="some_invalid_value"))
}

doTest("Aggregator Test: Validity of transform parameter values", test.aggregator.params.transform)
