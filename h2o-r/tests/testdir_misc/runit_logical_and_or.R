setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
##
# Testing as.logical
##




# setupRandomSeed(1994831827)

test <- function() {
  x <- c(T,F,NA)
  y <- c(T,F,NA)
  for (x_i in x) {
    for (y_i in y) {
      h2o_and_R_equal( (as.h2o(as.numeric(x_i)) && as.h2o(as.numeric(y_i))) , (x_i && y_i))
      h2o_and_R_equal( (as.h2o(as.numeric(x_i)) || as.h2o(as.numeric(y_i))) , (x_i || y_i))
      h2o_and_R_equal( (as.h2o(as.numeric(x_i)) & as.h2o(as.numeric(y_i)))  , (x_i & y_i))
      h2o_and_R_equal( (as.h2o(as.numeric(x_i)) | as.h2o(as.numeric(y_i)))  , (x_i | y_i))
    }
  }
}

doTest("Test as.logical", test)

