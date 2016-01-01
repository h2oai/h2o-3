setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.quantile.golden <- function() {
  probs <- c(0.01, 0.05, 0.1, 0.25, 0.333, 0.5, 0.667, 0.75, 0.9, 0.95, 0.99)
  probs.rand <- sort(round(runif(10, 0, 1), 6))
  
  vec <- rnorm(1000)
  vec.hex <- as.h2o(vec)
  
  h2oTest.logInfo("Check errors generated for probabilities outside [0,1]")
  expect_error(quantile(vec.hex, probs = -0.2))
  expect_error(quantile(vec.hex, probs = 1.2))
  expect_error(quantile(vec.hex, probs = c(0.1, -0.5, 0.2, 1.5)))
  
  h2oTest.logInfo("Check min/max equal to 0% and 100% quantiles")
  expect_equal(as.data.frame(quantile(vec.hex, probs = 0))[1,1], min(vec.hex))
  expect_equal(as.data.frame(quantile(vec.hex, probs = 1))[1,1], max(vec.hex))
  
  h2oTest.logInfo("Check constant vector returns constant for all quantiles")
  vec.cons <- rep(5,1000)
  vec.cons.hex <- as.h2o(vec.cons)
  expect_true(all(as.data.frame(quantile(vec.cons.hex, probs = probs)) == 5))
  
  h2oTest.logInfo("Check quantiles are identical to R with type = 7")
  quant.r <- as.vector(quantile(vec, probs = probs, type = 7))
  quant.h2o <- as.data.frame(quantile(vec.hex, probs = probs))[,1]
  expect_equal(quant.h2o, quant.r)
  
  quant.rand.r <- as.vector(quantile(vec, probs = probs.rand, type = 7))
  quant.rand.h2o <- as.data.frame(quantile(vec.hex, probs = probs.rand))[,1]
  expect_equal(quant.rand.h2o, quant.rand.r)
  
  h2oTest.logInfo("Check missing values are ignored in calculation")
  vecNA <- vec; vecNA[sample(1000,100)] <- NA
  vecNA.hex <- as.h2o(vecNA)
  
  quantNA.r <- as.vector(quantile(vecNA, probs = probs, type = 7, na.rm = TRUE))
  quantNA.h2o <- as.data.frame(quantile(vecNA.hex, probs = probs))[,1]
  expect_equal(quantNA.h2o, quantNA.r)
  
  quantNA.rand.r <- as.vector(quantile(vecNA, probs = probs.rand, type = 7, na.rm = TRUE))
  quantNA.rand.h2o <- as.data.frame(quantile(vecNA.hex, probs = probs.rand))[,1]
  expect_equal(quantNA.rand.h2o, quantNA.rand.r)

  h2oTest.logInfo("Check interpolation matches R with type=7 (pubdev-671)")
  getNumbers = function(s)as.numeric(lapply(strsplit(s[,1], ":"), '[', 2))   # even in base R, summary.data.frame returns formatted text
  probs = seq(0,1,by=0.01)
  for (vec in list(
       c(5 , 8 , 9 , 12 , 13 , 16 , 18 , 23 , 27 , 28 , 30 , 31 , 33 , 34 , 43, 45, 48, 161)   # unique
      ,c(5 , 8 , 9 , 9 , 9 , 16 , 18 , 23 , 27 , 28 , 30 , 31 , 31 , 34 , 43, 43, 43, 161)     # some dups
      , c(rep(1,10), rep(21,6), rep(3.9,7))                                                    # stride from Nidhi
  )) {
      vec.hex <- as.h2o(vec)

      # summary() fetches precomputed quantiles from rollup [what pubdev-671 is about]
      expect_equal(getNumbers(summary(as.data.frame(vec))), getNumbers(summary(vec.hex)), tolerance=(max(vec)-min(vec))/1000)

      # quantile() on the other hand is recomputed and is exact
      expect_equal(as.vector(quantile(vec, probs=probs, type=7)),
                   as.vector(quantile(vec.hex, probs=probs)))
  }

  
}

h2oTest.doTest("Quantile Test: Golden Quantile Test", test.quantile.golden)
