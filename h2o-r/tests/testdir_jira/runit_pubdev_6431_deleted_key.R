setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.getJobsWithDeletedKeys <- function() {

  input <- h2o.importFile(locate("smalldata/flow_examples/abalone.csv.gz"))
  model <- h2o.glm(model_id = "MyModel", y = "C9", training_frame = input, validation_frame = input, alpha = 0.3)
  expect_equal(2, nrow(h2o.list_jobs()))

  h2o.rm(model)
  expect_equal(2, nrow(h2o.list_jobs()))

  h2o.grid(algorithm = "glm", grid_id = "MyModel", y = "C9", training_frame = input, validation_frame = input, alpha = 0.3)
  expect_equal(3, nrow(h2o.list_jobs()))
}

doTest("List of jobs with deleted keys", test.getJobsWithDeletedKeys)
