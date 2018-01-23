setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

.checkCorrectNumExemplars <- function(dataset, target_exemplars, rel_tol) {
  Log.info(paste("Starting test with values NumExamplars=", target_exemplars, " Tol: ", rel_tol))
  expect_that(
    agg <- h2o.aggregator(
      model_id="agg",
      training_frame=dataset,
      target_num_exemplars=target_exemplars,
      rel_tol_num_exemplars=rel_tol
    ),
    not(throws_error()),
    "Setting valid aggregator parameters should not throw an error!"
  )
  nf <- h2o.aggregated_frame(agg)
  expect_equal(
    h2o.nrow(nf), target_exemplars, scale=1, tol=(rel_tol*target_exemplars),
    info="Final number of aggregated exemplars should be equal to target number +/- tolerance"
  )
}

test.aggregator.numExemplars <- function() {
  df <- h2o.createFrame(
    rows=10000,
    cols=3,
    categorical_fraction=0.1,
    integer_fraction=0.3,
    real_range=100,
    missing_fraction=0,
    seed=123
  )
  .checkCorrectNumExemplars(df, 10, 0.95)
  .checkCorrectNumExemplars(df, 100, 0.5)
  .checkCorrectNumExemplars(df, 500, 0.3)
  .checkCorrectNumExemplars(df, 1500, 0.05)

}

doTest("Aggregator target_num_exemplars test", test.aggregator.numExemplars)
