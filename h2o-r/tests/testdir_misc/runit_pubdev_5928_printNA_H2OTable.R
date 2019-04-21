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
  # correct weighted stats
  ## Import prostate dataset
  prostate_hex <- h2o.uploadFile(locate("smalldata/prostate/prostate_NA_weights.csv")) # constant weight C0, vary C10

  ## Change CAPSULE to Enum
  prostate_hex[, "CAPSULE"] <- as.factor(prostate_hex[, "CAPSULE"]) # should be enum by default
  ## build GBM model
  prostate_gbm <- h2o.gbm(x = c('CAPSULE', 'AGE', 'RACE', 'DPROS', 'DCAPS', 'PSA', 'VOL'), y = "GLEASON", 
                         training_frame = prostate_hex, ntrees = 50, learn_rate=0.05, seed = 12345)
  
  ## Calculate partial dependence using h2o.partialPlot for columns "AGE" and "RACE"
  # build pdp without weight or NA
  h2o_pp_weight_NA <- h2o.partialPlot(object = prostate_gbm, data = prostate_hex, cols = c("AGE", "RACE"), nbins=3, plot = F, weight_column="constWeight", include_na=TRUE)
  h2o2DtablePrintOut <- capture.output(h2o_pp_weight_NA[[1]]) # capture H2OTable print here
  naNum <- sum(grepl("NA", h2o2DtablePrintOut))  # should be 1
}

doTest("Test Partial Dependence Plots with weights and NAs in H2O: ", testPartialPlots)

