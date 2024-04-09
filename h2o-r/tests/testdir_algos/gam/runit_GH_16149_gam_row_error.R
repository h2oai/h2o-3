setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

library(data.table)
library(dplyr)
library(h2o) # or load your h2o in a different way
library(ggplot2)


test.model.gam.dataset.error <- function() {
  # for n=1001 ==========================================================================================
  n <- 1001
  sum_insured <- seq(1, 200000, length.out = n)
browser()  
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
  
  pred2 <-
    h2o.predict(object = model2, newdata = h2o_data2) %>% as.vector
  
  # plot result
  d2$pred <- pred2
  ggplot(d2) + geom_line(aes(x = x2, y = y)) + geom_line(aes(x = x2, y = pred), colour =
                                                           'red') + scale_x_continuous(breaks = seq(0, 200000, by = 20000))
  ggsave('H:/h2o gam monotonic decreasing.png')
}

doTest("General Additive Model dataset size 1001 error", test.model.gam.dataset.error)