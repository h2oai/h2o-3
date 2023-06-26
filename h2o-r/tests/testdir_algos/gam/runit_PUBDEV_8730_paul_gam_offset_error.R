setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# This test is provided by Paul in https://github.com/h2oai/h2o-3/issues/6980
# This test is very clevel in it the prediction from both models will be the same.
testGAMOffset <- function() {
  mt <- as.h2o(mtcars)

  mod <- h2o.gam(
  x = c("cyl", "am"),
  y = "mpg",
  training_frame = mt,
  lambda = 0, 
  keep_gam_cols = TRUE,
  gam_columns = "hp",
  num_knots = 3, 
  spline_orders = 3,
  bs = 0, 
  scale = 0.01)
  predNoOffset <- h2o.predict(object = mod, newdata = mt)

  mt$am_offset <- mod@model$coefficients["am"]*mt$am
  
  mod_w_offset <- h2o.gam(
    x = c("cyl"),
    y = "mpg",
    training_frame = mt,
    lambda = 0, 
    keep_gam_cols = TRUE,
    offset_column = "am_offset",
    gam_columns = "hp",
    num_knots = 3, 
    spline_orders = 3,
    bs = 0, 
    scale = 0.01)
  
  predWOffset <- h2o.predict(object = mod_w_offset, newdata = mt)
  compareFrames(predNoOffset, predWOffset) # clever example which will make the two models return the same prediction
}

doTest("GAM tweedie Test: GAM w/ offset and weights", testGAMOffset)
