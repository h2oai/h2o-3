setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.aggregator.params.cat_encoding <- function() {
  valid_values <- c(
    "AUTO", "Enum", "OneHotInternal",
    "OneHotExplicit", "Binary", "Eigen",
    "LabelEncoder", "EnumLimited"
  )
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
      h2o.aggregator(training_frame=df, categorical_encoding=val),
      not(throws_error()),
      "Valid categorical_encoding parameter should not throw an error!"
    )
  }

  expect_error(h2o.aggregator(training_frame=df, categorical_encoding="some_invalid_value"))
}

doTest("Aggregator Test: Validity of categorical_encoding parameter values", test.aggregator.params.cat_encoding)
