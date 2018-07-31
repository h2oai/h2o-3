setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.as.h2o.ordered <- function() {
    df <- data.frame(x = ordered(1:3), y = 4:6)
    df.hex <- as.h2o(df)

    df$x <- as.factor(as.integer(df$x)) # when converting back to R with as.data.frame it will be converted to `factor`
    expect_equal(df, as.data.frame(df.hex))
}

doTest("Testing as.h2o on a data.frame that contains column of type ordered", test.as.h2o.ordered)
