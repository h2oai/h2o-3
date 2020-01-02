setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.cor <- function() {
  data <- as.h2o(iris)

  cor_R = cor(iris[1], iris[3], method = "spearman")
  cor_h2o = h2o.cor(x = data[1], y = data[3], method = "Spearman")
  # R appears to be using the simplified formula to estimate Spearman's Rho
  expect_equal(cor_R[1,1], cor_h2o, tolerance = 0.01)
}

doTest("Test out the cor() functionality", test.cor)