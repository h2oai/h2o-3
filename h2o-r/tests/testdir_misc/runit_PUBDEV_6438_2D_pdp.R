setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test <- function() {
  ## Import prostate dataset
  prostate_hex = h2o.uploadFile(locate("smalldata/prostate/prostate_cat_NA.csv"), "prostate.hex")

  ## Change CAPSULE to Enum
  prostate_hex[, "CAPSULE"] = as.factor(prostate_hex[, "CAPSULE"])
  ## Run Random Forest in H2O
  prostate_drf = h2o.randomForest(x = c("AGE", "RACE"), y = "CAPSULE", training_frame = prostate_hex, ntrees = 50, seed = 12345)
  ageSplit = c(43.0, 44.89473684210526, 46.78947368421053, 48.68421052631579, 50.578947368421055,
               52.473684210526315, 54.368421052631575, 56.26315789473684, 58.1578947368421,
               60.05263157894737, 61.94736842105263, 63.84210526315789, 65.73684210526315,
               67.63157894736842, 69.52631578947368, 71.42105263157895, 73.3157894736842,
               75.21052631578948, 77.10526315789474) 
  user_splits_list = list(c("AGE", ageSplit))
  temp_filename_no_extension <- tempfile(pattern = "pdp", tmpdir = tempdir(), fileext = "")
  ## Calculate partial dependence using h2o.partialPlot for columns "AGE" and "RACE"
  h2o_pp_1d_2d = h2o.partialPlot(object = prostate_drf, newdata = prostate_hex, cols = c("RACE", "AGE"), 
                                 col_pairs_2dpdp=list(c("RACE", "AGE"), c("AGE", "PSA")), plot = TRUE, 
                                 user_splits=user_splits_list, save_to=temp_filename_no_extension)
  if (file.exists(temp_filename_no_extension)) 
    file.remove(temp_filename_no_extension)
  
  h2o_pp_2d_only = h2o.partialPlot(object = prostate_drf, newdata = prostate_hex, col_pairs_2dpdp=list(c("RACE", "AGE"), c("AGE", "PSA")), plot = FALSE,
                                   user_splits=user_splits_list)
  h2o_pp_1d_only = h2o.partialPlot(object = prostate_drf, newdata = prostate_hex, cols = c("RACE", "AGE"), plot = FALSE, user_splits=user_splits_list)

   # compare 1d pdp results from 1d pdp only and from 1d and 2d pdps 
  assert_partialPlots_twoDTable_equal(h2o_pp_1d_2d[[1]],h2o_pp_1d_only[[1]])  # compare RACE
  assert_partialPlots_twoDTable_equal(h2o_pp_1d_2d[[2]],h2o_pp_1d_only[[2]])  # for AGE
  # compare 2d pdp results from 2d pdp only and from 1d and 2d pdps  
  assert_partialPlots_twoDTable_equal(h2o_pp_1d_2d[[3]],h2o_pp_2d_only[[1]])  # 2d pdp RACE and AGE
  assert_partialPlots_twoDTable_equal(h2o_pp_1d_2d[[4]],h2o_pp_2d_only[[2]])  # 2d pdp PSA and VOL
}

doTest("Test 2D Partial Dependence Plots in H2O: ", test)

