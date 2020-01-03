setwd(normalizePath(dirname(
  R.utils::commandArgs(asValues = TRUE)$"f"
)))
source("../../../scripts/h2o-r-test-setup.R")

test.cor <- function() {
  data <- as.h2o(iris)
  
  cor_R = cor(iris[1], iris[3], method = "spearman")
  cor_h2o = h2o.cor(x = data[1], y = data[3], method = "Spearman")
  # R appears to be using the simplified formula to estimate Spearman's Rho
  expect_equal(cor_R[1, 1], cor_h2o, tolerance = 0.01)
  
  weather <- h2o.importFile(locate("smalldata/junit/weather.csv"))
  
  # Incomplete observation settings - "everything" mode - NaN result if NaN observation is found
  cor_h2o <- h2o.cor(x = weather, method = "spearman", use = "everything")
  expect_true(is.nan(cor_h2o[2, 2]))
  expect_equal(24, h2o.ncol(cor_h2o))
  
  # Incomplete observation settiongs - "complete.obs" mode - observations with NaN are skipped (not resulting in NaN)
  cor_h2o <- h2o.cor(x = weather, method = "spearman", use = "complete.obs")
  expect_false(is.nan(cor_h2o[2, 2]))
  
  # Incomplete observation settiongs - "all.obs" mode - if there is an observation with NaN found, an error is thrown
  expect_error(
    h2o.cor(x = weather, method = "spearman", use = "all.obs"),
    "Mode is 'AllObs' but NAs are present"
  )
  
}

doTest("Test out the cor() functionality", test.cor)