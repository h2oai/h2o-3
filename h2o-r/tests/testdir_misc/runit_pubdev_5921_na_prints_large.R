setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

testPartialPlots <- function() {
  ## Import prostate dataset
  airlines_hex <- h2o.importFile(locate("smalldata/airlines/AirlinesTrainWgt.csv"), na.strings="NA")
  weigth_col <- "Weight"
  x <- c('fYear', 'fMonth', 'fDayofMonth', 'fDayOfWeek', 'DepTime','ArrTime', 'UniqueCarrier', 'Origin', 'Dest', 'Distance', 'Input_miss')
  ## build GBM model
  airlines_gbm <- h2o.gbm(x = x, y = "IsDepDelayed", training_frame = airlines_hex, ntrees = 80, learn_rate=0.1, seed = 12345)
  # build pdp without weight or NA
  h2o_pp_weight <- h2o.partialPlot(object = airlines_gbm, data = airlines_hex, cols = c("Input_miss", "fDayOfWeek"), plot = T, weight_column=weigth_col)
  h2o_pp_weight_NA <- h2o.partialPlot(object = airlines_gbm, data =  airlines_hex, cols = c("Input_miss", "fDayOfWeek"), plot = T, weight_column=weigth_col, include_na=TRUE)
  
  assert_twoDTable_equal(h2o_pp_weight[[1]], h2o_pp_weight_NA[[1]]) # compare Input_miss pdp
  assert_twoDTable_equal(h2o_pp_weight[[2]], h2o_pp_weight_NA[[2]]) # compare fDayOfWeek pdp
  browser()
  manual_weighted_stats_im <- manual_partial_dependency(airlines_gbm,  airlines_hex, h2o_pp_weight_NA[[1]][[1]], "Input_miss", as.data.frame(airlines_hex["Weight"]), 3)
  assert_twoDTable_array_equal(h2o_pp_weight_NA[[1]], manual_weighted_stats_im[1,], manual_weighted_stats_im[2,], manual_weighted_stats_im[3,])
  manual_weighted_stats_day <- manual_partial_dependency(airlines_gbm,  airlines_hex, h2o_pp_weight_NA[[2]][[1]], "fDayOfWeek", as.data.frame(airlines_hex["Weight"]), 3)
  assert_twoDTable_array_equal(h2o_pp_weight_NA[[2]], manual_weighted_stats_day[1,], manual_weighted_stats_day[2,], manual_weighted_stats_day[3,])
}

doTest("Test Partial Dependence Plots with weights and NAs in H2O: ", testPartialPlots)

