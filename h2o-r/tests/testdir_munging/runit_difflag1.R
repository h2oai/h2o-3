setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


test.difflag1 <- function() {
  x <- runif(1:1000000)
  fr <- as.h2o(x)
  diff_r <- diff(x)
  diff_h2o <- h2o.difflag1(fr)
  diff_h2o <- diff_h2o[2:1000000] #Here it is 2:1000000 because we add a NaN to the first row since
                                  #there is no previous row to get a diff from.

  h2o_df <- as.data.frame(diff_h2o)

  h2o_vec <- as.vector(unlist(h2o_df))
  r_vec   <- as.vector(unlist(diff_r))

  expect_equal(h2o_vec,r_vec,tol=1e-3)

}

doTest("Test difflag1", test.difflag1)