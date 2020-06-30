setwd(normalizePath(dirname(
  R.utils::commandArgs(asValues = TRUE)$"f"
)))
source("../../scripts/h2o-r-test-setup.R")



test.kolmogorov_smirnov <- function() {
  data <- h2o.importFile(path = locate('smalldata/testng/airlines_train.csv'))
  model <- h2o.gbm(x = c("Origin", "Distance"), y = "IsDepDelayed", training_frame = data, ntrees = 1, gainslift_bins = 500)
  
  print(h2o.gainsLift(model))
  expect_false(is.null(h2o.gainsLift(model)$kolmogorov_smirnov))
  kolmogorov_smirnov <- h2o.kolmogorov_smirnov(model)
  print(kolmogorov_smirnov)
  expect_true(is.numeric(kolmogorov_smirnov))
  expect_true(kolmogorov_smirnov > 0 && kolmogorov_smirnov < 1)
  
  metrics <- model@model$training_metrics
  kolmogorov_smirnov <- h2o.kolmogorov_smirnov(metrics)
  print(kolmogorov_smirnov)
  expect_true(is.numeric(kolmogorov_smirnov))
  expect_true(kolmogorov_smirnov > 0 && kolmogorov_smirnov < 1)
}

doTest("PUBDEV-7404: Kolmogorov-Smirnov metric",
       test.kolmogorov_smirnov)
