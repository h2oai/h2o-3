setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
################################################################################
## PUBDEV-1572
##
## In the case of multiple supplied ratios, h2o.splitFrame shouldn't treat the
## ratios as relative ratios
##
################################################################################




test.splitFrame.multiple.ratios <- function() {
  hex <- as.h2o(iris)

  h2oTest.logInfo("Splits using c(0.1, 0.2), c(0.2, 0.4), c(0.3, 0.6).")
  split_1 <- h2o.splitFrame(hex, c(0.1, 0.2))
  split_2 <- h2o.splitFrame(hex, c(0.2, 0.4))
  split_3 <- h2o.splitFrame(hex, c(0.3, 0.6))

  small_1 <- nrow(split_1[[1]])
  large_1 <- nrow(split_1[[2]])
  small_2 <- nrow(split_2[[1]])
  large_2 <- nrow(split_2[[2]])
  small_3 <- nrow(split_3[[1]])
  large_3 <- nrow(split_3[[2]])

  expect_equal(large_3, 3*large_1)
  expect_equal(large_2, 2*large_1)
  expect_equal(small_3, 3*small_1)
  expect_equal(small_2, 2*small_1)

  
}

h2oTest.doTest("Using Splitframe on Multiple Ratios", test.splitFrame.multiple.ratios)
