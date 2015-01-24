setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.quantile.golden <- function(conn) {
  probs <- c(0.01, 0.05, 0.1, 0.25, 0.333, 0.5, 0.667, 0.75, 0.9, 0.95, 0.99)
  probs.rand <- sort(round(runif(10, 0, 1), 6))
  
  vec <- rnorm(1000)
  vec.hex <- as.h2o(conn, vec)
  
  Log.info("Check errors generated for probabilities outside [0,1]")
  expect_error(quantile(vec.hex, probs = -0.2))
  expect_error(quantile(vec.hex, probs = 1.2))
  expect_error(quantile(vec.hex, probs = c(0.1, -0.5, 0.2, 1.5)))
  
  Log.info("Check min/max equal to 0% and 100% quantiles")
  expect_equal(as.numeric(quantile(vec.hex, probs = 0)), min(vec.hex))
  expect_equal(as.numeric(quantile(vec.hex, probs = 1)), max(vec.hex))
  
  Log.info("Check constant vector returns constant for all quantiles")
  vec.cons <- rep(5,1000)
  vec.cons.hex <- as.h2o(conn, vec.cons)
  expect_true(all(quantile(vec.cons.hex, probs = probs) == 5))
  
  Log.info("Check quantiles are identical to R with type = 7")
  quant.r <- quantile(vec, probs = probs, type = 7)
  quant.h2o <- quantile(vec.hex, probs = probs)
  expect_equal(quant.h2o, quant.r)
  
  quant.rand.r <- quantile(vec, probs = probs.rand, type = 7)
  quant.rand.h2o <- quantile(vec.hex, probs = probs.rand)
  expect_equal(quant.rand.h2o, quant.rand.r)
  
  Log.info("Check missing values are ignored in calculation")
  vecNA <- vec; vecNA[sample(1000,100)] <- NA
  vecNA.hex <- as.h2o(conn, vecNA)
  
  quantNA.r <- quantile(vecNA, probs = probs, type = 7, na.rm = TRUE)
  quantNA.h2o <- quantile(vecNA.hex, probs = probs)
  expect_equal(quantNA.h2o, quantNA.r)
  
  quantNA.rand.r <- quantile(vecNA, probs = probs.rand, type = 7, na.rm = TRUE)
  quantNA.rand.h2o <- quantile(vecNA.hex, probs = probs.rand)
  expect_equal(quantNA.rand.h2o, quantNA.rand.r)
  
  testEnd()
}

doTest("Quantile Test: Golden Quantile Test", test.quantile.golden)