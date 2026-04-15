setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test <- function() {
  ## Import prostate dataset
  prostate_hex = h2o.uploadFile(locate("smalldata/prostate/prostate_cat_NA.csv"), "prostate.hex")

  ## Change CAPSULE to Enum
  prostate_hex[, "CAPSULE"] = as.factor(prostate_hex[, "CAPSULE"])
  ## Run Random Forest in H2O
  temp_filename_no_extension <- tempfile(pattern = "pdp", tmpdir = tempdir(), fileext = "")
  ## Calculate partial dependence using h2o.partialPlot for columns "AGE" and "RACE"
  prostate_drf = h2o.randomForest(x = c("AGE", "RACE"), y = "CAPSULE", training_frame = prostate_hex, ntrees = 50, seed = 12345)
  h2o_pp_1d_2d = h2o.partialPlot(object = prostate_drf, newdata = prostate_hex, cols = c("RACE", "AGE"), 
                                 col_pairs_2dpdp=list(c("RACE", "AGE"), c("AGE", "PSA")), plot = T, 
                                 save_to=temp_filename_no_extension)
  h2o_pp_2d_only = h2o.partialPlot(object = prostate_drf, newdata = prostate_hex, col_pairs_2dpdp=list(c("RACE", "AGE"), c("AGE", "PSA")),
                                   plot = FALSE)
  # compare 2d pdp results from 2d pdp only and from 1d and 2d pdps  
  assert_partialPlots_twoDTable_equal(h2o_pp_1d_2d[[3]],h2o_pp_2d_only[[1]])  # 2d pdp RACE and AGE
  assert_partialPlots_twoDTable_equal(h2o_pp_1d_2d[[4]],h2o_pp_2d_only[[2]])  # 2d pdp PSA and VOL
  if (file.exists(temp_filename_no_extension)) 
    file.remove(temp_filename_no_extension)
}

doTest("Test 2D Partial Dependence Plots in H2O: ", test)

