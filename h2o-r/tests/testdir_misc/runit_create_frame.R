setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.createFrame <- function(conn) {
  hex <- h2o.createFrame(conn, rows = 10000, cols = 100, categorical_fraction = 0.1, factors = 5, integer_fraction = 0.5, integer_range = 1, response_factors = 2, randomize = TRUE)
  expect_equal(dim(hex), c(10000, 100))
  expect_equal(length(colnames(hex)), 100)
  print(colnames(hex))
  
#   num_fac = 0
#   for(i in 2:101) {
#     if(is.factor(hex[,i]))
#       num_fac = num_fac + 1
#   }
#   expect_equal(num_fac/100, categorical_fraction)
  testEnd()
}

doTest("Create a random data frame in H2O", test.createFrame)