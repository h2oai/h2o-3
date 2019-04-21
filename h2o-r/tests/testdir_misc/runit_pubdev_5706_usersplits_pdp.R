setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

##
# PUBDEV-5706: Allow user defined split points for partialplots.
#
# In this test, we will test a regression model while pyunit tested a classifier model.
#
# This test is used to make sure users can specify their own split points.  The following will be done:
#  1. build a pdp without any user split point with 3 columns, one numeric, two enum columns
#  2. build a pdp with user defined-split points for one numeric, and one enum column.  We will use the actual
# features used by 1 but shorten it.
#
# We compare results from 1 and 2 and they should agree up to the length of the shorter one.
##

testpdpUserSplits <- function() {
  
  prostate_hex = h2o.uploadFile(locate("smalldata/prostate/prostate_NA_weights.csv")) # constant weight C0, vary C10
  
  ## Change CAPSULE to Enum
  prostate_hex[, "CAPSULE"] = as.factor(prostate_hex[, "CAPSULE"]) # should be enum by default
  ## build GBM model
  prostate_gbm = h2o.gbm(x = c('CAPSULE', 'AGE', 'RACE', 'DPROS', 'DCAPS', 'PSA', 'VOL'), y = "GLEASON", 
                         training_frame = prostate_hex, ntrees = 50, learn_rate=0.05, seed = 12345)
  
  # build pdp normal
  h2o_pp = h2o.partialPlot(object = prostate_gbm, data = prostate_hex, cols = c("AGE", "RACE", "DCAPS"), plot = F)
  ## Calculate partial dependence using h2o.partialPlot for columns "AGE" and "RACE"
  ageSplit = c(43.0, 44.89473684210526, 46.78947368421053, 48.68421052631579, 50.578947368421055,
               52.473684210526315, 54.368421052631575, 56.26315789473684, 58.1578947368421,
               60.05263157894737, 61.94736842105263, 63.84210526315789, 65.73684210526315,
               67.63157894736842, 69.52631578947368, 71.42105263157895, 73.3157894736842,
               75.21052631578948, 77.10526315789474)
  raceSplit = c("Black")  
  user_splits_list = list(c("AGE", ageSplit), c("RACE", raceSplit))
  h2o_pp_splits = h2o.partialPlot(object = prostate_gbm, data = prostate_hex, cols = c("AGE", "RACE", "DCAPS"), plot = F, user_splits=user_splits_list)
  
  # build pdp normal
  h2o_pp = h2o.partialPlot(object = prostate_gbm, data = prostate_hex, cols = c("AGE", "RACE", "DCAPS"), plot = F)
  # compare pdp from both.  They are not the same length.  The user split is one shorter.
  assert_partialPlots_twoDTable_equal(h2o_pp_splits[[1]],h2o_pp[[1]]) # for AGE
  assert_partialPlots_twoDTable_equal(h2o_pp_splits[[2]],h2o_pp[[2]])  # for RACE
  assert_partialPlots_twoDTable_equal(h2o_pp[[3]], h2o_pp_splits[[3]])  # for DCAPS, should be the same length
}

doTest("Test Partial Dependence Plots in H2O with User defined split points: ", testpdpUserSplits)

