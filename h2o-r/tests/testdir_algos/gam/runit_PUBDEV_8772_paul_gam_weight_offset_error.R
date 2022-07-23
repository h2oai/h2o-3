setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.gam.weight.offset <- function() {
    mt <- as.h2o(mtcars)
    mt$weights <- 1 # simplest case, should be the same as no weight
    args <- list(
          x = c("cyl", "am"),
          y = "mpg",
          training_frame = as.name("mt"),
          lambda = 0, 
          keep_gam_cols = TRUE,
          gam_columns = "hp",
          num_knots = 3, 
          spline_orders = 3,
          bs = 0, 
          scale = 0.01
        )
    mod <- do.call(what = "h2o.gam", args = args) # original model with no weight and no offset

    # add offset based on starting model
    mt$am_offset <- mod@model$coefficients["am"]*mt$am
    
    args$x <- "cyl" # swapping modeled 'am' with 'am_offset'
    # only offset, no weight
    mod_w_offset <- do.call(what = "h2o.gam", args = c(args, offset_column = "am_offset"))
    pred_offset <- h2o.predict(object = mod_w_offset, newdata = mt) # match
    residual_offset <- h2o.residual_deviance(mod_w_offset) # match
    
    # adding weights column and 'am_offset'
    mod_w_offset_and_weight <- do.call(what = "h2o.gam", args = c(args, offset_column = "am_offset", weights_column = "weights"))
    pred_w_offset <- h2o.predict(object = mod_w_offset_and_weight, newdata = mt) # NO MATCH
    compareFrames(pred_offset, pred_w_offset, prob=1, tolerance=1e-6)
    
    residual_w_offset <- h2o.residual_deviance(mod_w_offset_and_weight) # match
    expect_true(abs(residual_offset-residual_w_offset) < 1e-6)
}

doTest("General Additive Model test weight and offset columns", test.gam.weight.offset)
