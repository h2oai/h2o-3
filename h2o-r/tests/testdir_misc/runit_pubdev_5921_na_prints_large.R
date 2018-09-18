setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

testPartialPlots <- function() {
  ## Import prostate dataset
  airlines_hex = h2o.importFile(locate("smalldata/airlines/AirlinesTrainWgt.csv"), na.strings="NA")
  test = h2o.importFile(locate("smalldata/airlines/AirlinesTrainWgt.csv"), na.strings="NA")
  browser()
  weigth_col = "Weight"
  x = c('fYear', 'fMonth', 'fDayofMonth', 'fDayOfWeek', 'DepTime','ArrTime', 'UniqueCarrier', 'Origin', 'Dest', 'Distance', 'Input_miss')
  ## build GBM model
  airlines_gbm = h2o.gbm(x = x, y = "IsDepDelayed", training_frame = airlines_hex, ntrees = 80, learn_rate=0.1, seed = 12345)
  # build pdp without weight or NA
  h2o_pp = h2o.partialPlot(object = airlines_gbm, data = test, cols = c("Input_miss"), nbins=3, plot = T)
  browser()
  xlist = h2o_pp$Input_miss
  manual_weighted_stats <- compare_weighted_stats(airlines_gbm, test, xlist, "Input_miss", as.data.frame(test["Weight"]), 3)
  browser()
  h2o_pp_weight = h2o.partialPlot(object = airlines_gbm, data = test, cols = c("Input_miss"), nbins=3, plot = T, weight_column=weigth_col)
  h2o_pp_weight_NA = h2o.partialPlot(object = airlines_gbm, data = test, cols = c("Input_miss"),nbins=3, plot = T, weight_column=weigth_col, include_na=TRUE)
  print("done!")
}

doTest("Test Partial Dependence Plots with weights and NAs in H2O: ", testPartialPlots)

