setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
##
# Build HGLM Model and check against models from model outputs.
##

test.HGLMData1 <- function() {
  h2odata <- h2o.importFile(locate("smalldata/hglm_test/gaussian_0GC_allRC_2enum2numeric_p5oise_p08T_wIntercept_standardize.gz"))

  yresp <- "response"
  random_columns <- c("C2", "C3", "C4", "C5")
  group_column <- "C1"
  predictor <- c("C2","C3","C4","C5")
  hglm_model <- h2o.hglm(x=predictor, y=yresp, training_frame=h2odata, group_column=group_column, random_columns=random_columns,
                         seed=12345, max_iterations=10, em_epsilon=0.0000001, random_intercept=TRUE)
  coeff <- h2o.coef(hglm_model)
  coeff_random_effects <- h2o.coef_random(hglm_model)
  level_2_names <- h2o.level_2_names(hglm_model)
  coefs_random_names <- h2o.coefs_random_names(hglm_model)
  scoring_history <- h2o.scoring_history(hglm_model)
  t_mat <- h2o.matrix_T(hglm_model)
  residual_var <- h2o.residual_variance(hglm_model)
  icc <- h2o.icc(hglm_model)
  mean_res_fixed <- h2o.mean_residual_fixed(hglm_model)
  mse <- h2o.mse(hglm_model)
  # check and make sure nothing is null.
  expect_true(mse < mean_res_fixed)
  expect_true(length(icc)==length(coeff))
  expect_true(length(level_2_names)*length(coefs_random_names) == length(coeff_random_effects))
  expect_true(residual_var < mean_res_fixed)
  expect_true(length(scoring_history[[1]]) <= 10)
  print(hglm_model)
 }

doTest("Check HGLM model building, coefficients, model summary, scoring history....", test.HGLMData1)


