setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pubdev_4739 <- function() {
    feature.cols <- paste0("vote", 1:16)
    voting.data.raw <- h2o.importFile(path = locate('smalldata/extdata/housevotes.csv'),
    col.names = c("party", feature.cols),
    col.types = rep("string", 17))

    newdf <- h2o.sub("\\\\?", "n", x = voting.data.raw)
    expect_true(all.equal(names(newdf), names(voting.data.raw)))
}

doTest("PUBDEV-$4739: h2o.sub() wipes out column names", test.pubdev_4739)