setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")




test.klime.titanic.grid <- function() {
  # Load Titanic dataset (with predictions of 'Survived' made by GBM)
  titanic_input <- h2o.importFile(path = locate("smalldata/klime_test/titanic_input.csv"),
                                  col.types = c("enum","enum","real","real","real","enum","real","real","real","real"))
  titanic_expected <- h2o.importFile(path = locate("smalldata/klime_test/titanic_3_expected.csv"),
                                     col.types = c("real","real","real","real","real","real","real","real","real"))

  # Train a grid of k-LIME models
  klime.grid <- h2o.grid("klime", hyper_params = list(max_k = 8:12),
                         training_frame = titanic_input,
                         x = c("Age", "Pclass", "Sex", "SibSp", "Parch"), y = "p1",
                         estimate_k = FALSE, seed = 12345)

  summary.table <- klime.grid@summary_table
  expect_equal(colnames(summary.table), c("max_k", "model_ids", "r2"))
  expect_equal(summary.table[1, "max_k"], "10")
}

doTest("Test k-LIME on Titanic dataset with Grid Search", test.klime.titanic.grid)
