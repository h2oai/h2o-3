setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pubdev_673 <- function() {
  default <- c(0.001, 0.01, 0.1, 0.25, 0.333, 0.5, 0.667, 0.75, 0.9, 0.99, 0.999)
  xr <- data.frame(C1 = rep(NA_real_, 100))
  xh2o <- as.h2o(xr)
  
  expect_equal(quantile(xh2o), quantile(xr, probs = default, na.rm = TRUE))
  xr.quant <- quantile(xr, probs = c(0, 0.25, 0.5, 0.75, 1), na.rm = TRUE)
  xh2o.quant <- quantile(xh2o, probs = c(0, 0.25, 0.5, 0.75, 1))
  expect_equal(xh2o.quant, xr.quant)
  
  xr.sum <- summary(xr)
  xh2o.sum <- summary(xh2o)
  h2oTest.logInfo("R Summary:"); print(xr.sum)
  h2oTest.logInfo("H2O Summary:"); print(xh2o.sum)
  # expect_equal(xh2o.sum, xr.sum)
  
  
}

h2oTest.doTest("PUBDEV-673: H2O summary and quantile of all NA col", test.pubdev_673)
