setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# copied test from JIRA by Erin
test.model.gam.multiple.runs.npe <- function() {
  knots1 <- c(-1.99905699, -0.98143075, 0.02599159, 1.00770987, 1.99942290)
  frame_Knots1 <- as.h2o(knots1)
  h2o_data <- h2o.importFile(locate("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv"))
  h2o_data[["C1"]] <- as.factor(h2o_data[["C1"]])
  gam_model <- h2o.gam(x = "C6", y = "C1", training_frame = h2o_data, family = 'multinomial', gam_columns = "C6", scale = 1,
                       num_knots = 5, knot_ids = h2o.keyof(frame_Knots1))
  mse1 <- h2o.mse(gam_model)
  gam_model <- h2o.gam(x = "C6", y = "C1", training_frame = h2o_data, family = 'multinomial', gam_columns = "C6", scale = 1,
                       num_knots = 5, knot_ids = h2o.keyof(frame_Knots1))
  mse2 <- h2o.mse(gam_model)
  gam_model <- h2o.gam(x = "C6", y = "C1", training_frame = h2o_data, family = 'multinomial', gam_columns = "C6", scale = 1,
                       num_knots = 5, knot_ids = h2o.keyof(frame_Knots1))
  mse3 <- h2o.mse(gam_model)
  expect_true(abs(mse1-mse2) < 1e-6) # all metrics should equal
  expect_true(abs(mse2-mse3) < 1e-6)
}

doTest("General Additive Model test for multiple run NPE found", test.model.gam.multiple.runs.npe)

