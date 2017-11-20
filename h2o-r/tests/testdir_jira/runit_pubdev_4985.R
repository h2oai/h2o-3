setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pubdev.4985 <- function() {
    expected <- data.frame(a = factor(c("a", NA, NA, NA, NA, NA)), b = 42)
    data <- as.h2o(expected)
    split <- h2o.splitFrame(data, ratios = c(0.5), seed = 123)
    actual <- as.data.frame(h2o.rbind(split[[1]], split[[2]]))

    expect_equal(expected, actual)
}

doTest("PUBDEV-4985: Constant NA chunk doesn't have floats", test.pubdev.4985)
