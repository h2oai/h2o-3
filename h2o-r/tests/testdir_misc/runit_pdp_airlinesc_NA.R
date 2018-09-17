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
  airlines_hex = h2o.uploadFile("/Users/wendycwong/temp/GLMData/AirlinesTrainWgt.csv", na.strings="NA") 
  browser()
  ## Change CAPSULE to Enum
  prostate_hex[, "CAPSULE"] = as.factor(prostate_hex[, "CAPSULE"]) # should be enum by default
  weigth_col = "Weight"
  ## build GBM model
  airlines_gbm = h2o.gbm(x = c("fYear", "fMonth", "fDayofMonth", "fDayOfWeek", "UniqueCarrier", "Origin", "Dest" , 
                               "Distance" ), y = "IsDpeDelayed", training_frame = airlines_hex, ntrees = 50, learn_rate=0.05, seed = 12345)
  
  ## Calculate partial dependence using h2o.partialPlot for columns "AGE" and "RACE"
  # build pdp without weight or NA
  h2o_pp = h2o.partialPlot(object = airlines_gbm, data = airlines_hex, cols = c("UniqueCarrier", "Distance"), plot = T)
  h2o_pp_weight_NA = h2o.partialPlot(object = airlines_gbm, data = airlines_hex, cols = c("AGE", "RACE"), plot = F, weight_column=weigth_col, include_na=TRUE)
}

doTest("Test Partial Dependence Plots with weights and NAs in H2O: ", testPartialPlots)

