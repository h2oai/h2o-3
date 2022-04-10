setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.predicted_vs_actual_by_variable <- function() {
  df <- h2o.importFile(locate("smalldata/prostate/prostate_cat.csv"))
  m <- h2o.glm(training_frame=df, y="CAPSULE", family='binomial', lambda=0)

  predicted <- h2o.predict(m, df)

  predsVacts <- h2o.predicted_vs_actual_by_variable(m, df, predicted, "DPROS")
  pva_df <- as.data.frame(predsVacts)
  expect_equal(c("DPROS", "predict", "actual"), colnames(pva_df))
  expect_equal(4, nrow(pva_df))
  print(pva_df$DPROS)
  expect_equal(c("Both", "Left", "None", "Right"), pva_df$DPROS)
}

doTest("Test h2o.predicted_vs_actual_by_variable", test.predicted_vs_actual_by_variable)

