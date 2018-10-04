setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

# PUBDEV-5761 adding missing values and weights to partial plots
# In this test, I will build a GBM regression model and 
# 1. generate partialplots without weight column and without NAs
# 2. generate partialplots with constant weight column and with NAs
# 3. generate partialplots with varying weight and with NAs.
# 
# partial plots from 1 and 2 should agree except at the NA values
# partial plots from 3 will be compared to correct results generated from python.

testPartialPlots <- function() {
  ## Import prostate dataset
  prostate_hex <- h2o.uploadFile(locate("smalldata/prostate/prostate_NA_weights.csv")) # constant weight C0, vary C10

  ## Change CAPSULE to Enum
  prostate_hex[, "CAPSULE"] <- as.factor(prostate_hex[, "CAPSULE"]) # should be enum by default
  ## build GBM model
  prostate_gbm <- h2o.gbm(x = c('CAPSULE', 'AGE', 'RACE', 'DPROS', 'DCAPS', 'PSA', 'VOL'), y = "GLEASON", 
                         training_frame = prostate_hex, ntrees = 50, learn_rate=0.05, seed = 12345)
  
  ## Calculate partial dependence using h2o.partialPlot for columns "AGE" and "RACE"
  # build pdp without weight or NA
  h2o_pp <- h2o.partialPlot(object = prostate_gbm, data = prostate_hex, cols = c("AGE", "RACE"), plot = F)
  h2o_pp_weight_NA <- h2o.partialPlot(object = prostate_gbm, data = prostate_hex, cols = c("AGE", "RACE"), plot = F, weight_column="constWeight", include_na=TRUE)
  h2o_pp_vweight_NA <- h2o.partialPlot(object = prostate_gbm, data = prostate_hex, cols = c("AGE", "RACE"), plot = F, weight_column="variWeight", include_na=TRUE)
  
  assert_twoDTable_equal(h2o_pp[[1]], h2o_pp_weight_NA[[1]]) # compare RACE pdp
  assert_twoDTable_equal(h2o_pp[[2]], h2o_pp_weight_NA[[2]]) # compare AGE pdp
  
  # compare pdp with varying weight with correct answers derived from theoretical formulas
  manual_weighted_stats_age <- manual_partial_dependency(prostate_gbm, prostate_hex, h2o_pp_vweight_NA[[1]][[1]], "AGE", as.data.frame(prostate_hex["variWeight"]), 1)
  assert_twoDTable_array_equal(h2o_pp_vweight_NA[[1]], manual_weighted_stats_age[1,], manual_weighted_stats_age[2,], manual_weighted_stats_age[3,])
  manual_weighted_stats_race <- manual_partial_dependency(prostate_gbm, prostate_hex, h2o_pp_vweight_NA[[2]][[1]], "RACE", as.data.frame(prostate_hex["variWeight"]), 1)
 assert_twoDTable_array_equal(h2o_pp_vweight_NA[[2]], manual_weighted_stats_race[1,], manual_weighted_stats_race[2,], manual_weighted_stats_race[3,])
}

doTest("Test Partial Dependence Plots with weights and NAs in H2O: ", testPartialPlots)

