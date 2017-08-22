setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.string.distance <- function() {
    x <- as.h2o(c("Martha", "Dwayne", "Dixon", NA, NA))
    y <- as.character(as.h2o(c("Marhta", "Duane", "Dicksonx", "xxx", NA)))

    dist <- h2o.stringdist(x, y, method = "jw")

    expect_equal(as.data.frame(dist), data.frame(C1 = c(0.961, 0.84, 0.814, NA, NA)), tolerance = 0.001)
}

doTest("Testing String Distance", test.string.distance)