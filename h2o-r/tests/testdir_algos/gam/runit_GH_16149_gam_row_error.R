setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

library(data.table)

# This test was provided by a customer.  No exit condition is needed as
# the test before my fix always failed.  As long as this test completes
# successfully, it should be good enough.
test.gam.dataset.error <- function(n) {
  sum_insured <- seq(1, 200000, length.out = n)
  d2 <-
    data.table(
      sum_insured = sum_insured,
      sqrt = sqrt(sum_insured),
      sine = sin(2 * pi * sum_insured / 40000)
    )
  d2[, sine := 0.3 * sqrt * sine , ]
  d2[, y := pmax(0, sqrt + sine) , ]
  
  d2[, x := sum_insured]
  d2[, x2 := rev(x) , ] # flip axis
  
  # import the dataset
  h2o_data2 <- as.h2o(d2)
  
  model2 <-
    h2o.gam(
      y = "y",
      gam_columns = c("x2"),
      bs = c(2),
      spline_orders = c(3),
      splines_non_negative = c(F),
      training_frame = h2o_data2,
      family = "tweedie",
      tweedie_variance_power = 1.1,
      scale = c(0),
      lambda = 0,
      alpha = 0,
      keep_gam_cols = T,
      non_negative = TRUE,
      num_knots = c(10)
    )
  print("model building completed.")
}

test.model.gam.dataset.error <- function() {
  # test for n=1005
  test.gam.dataset.error(1005)
  # test for n=1001;
  test.gam.dataset.error(1001)
}

doTest("General Additive Model dataset size 1001 and 1005 error", test.model.gam.dataset.error)
