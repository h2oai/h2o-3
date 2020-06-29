setwd(normalizePath(dirname(R.utils::commandArgs(asValues = TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

automl.print.test <- function() {

  # Load data and split into train, valid and test sets
  train <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))
  y <- "CAPSULE"
  train[, y] <- as.factor(train[, y])
  max_models <- 3

  aml1 <- h2o.automl(y = y,
                     training_frame = train,
                     project_name = "r_aml_get_automl",
                     max_models = max_models,
                     seed = 1234)

  # Use h2o.get_automl to get previous automl instance
  get_aml1 <- h2o.get_automl(aml1@project_name)

  aml_printout <- captureOutput({ print(aml1) })
  get_aml_printout <- captureOutput({ print(get_aml1) })

  expect_equal(aml_printout, get_aml_printout)
  expect_true(any(grepl("Project Name:  r_aml_get_automl", aml_printout, fixed = TRUE)))

}


automl.summary.test <- function() {

  # Load data and split into train, valid and test sets
  train <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))
  y <- "CAPSULE"
  train[, y] <- as.factor(train[, y])
  max_models <- 3

  aml1 <- h2o.automl(y = y,
                     training_frame = train,
                     project_name = "r_aml_get_automl",
                     max_models = max_models,
                     seed = 1234)

  # Use h2o.get_automl to get previous automl instance
  get_aml1 <- h2o.get_automl(aml1@project_name)

  aml_printout <- captureOutput({ print(aml1) })
  aml_summary <- captureOutput({ summary(aml1) })
  get_aml_summary <- captureOutput({ summary(get_aml1) })

  expect_equal(aml_summary, get_aml_summary)

  # Summary should be more detailed than regular print
  expect_gte(length(aml_summary), length(aml_printout))

  expect_true(any(grepl("Project Name:  r_aml_get_automl", aml_printout, fixed = TRUE)))

  # Summary can be a bit more expensive so we show all columns in leaderboard
  expect_false(any(grepl("training_time_ms", aml_printout, fixed = TRUE)))
  expect_true(any(grepl("training_time_ms", aml_summary, fixed = TRUE)))

}

doSuite("AutoML summary Test", makeSuite(
  automl.print.test,
  automl.summary.test
))
