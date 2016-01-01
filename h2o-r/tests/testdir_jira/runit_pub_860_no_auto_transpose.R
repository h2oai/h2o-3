setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pub_860_no_auto_transpose <- function() {

prostatePath <- h2oTest.locate("smalldata/prostate/prostate.csv")
prostate.hex <- h2o.importFile(path = prostatePath, destination_frame = "prostate.hex")

prostate.local <- as.data.frame(prostate.hex)

# Are we in the right universe?
expect_equal(380, dim(prostate.local)[1])
expect_equal(9, dim(prostate.local)[2])

# local multiply with auto-transpose
loc <- prostate.local$AGE %*% prostate.local$CAPSULE
expect_equal(1, dim(loc)[1])
expect_equal(1, dim(loc)[2])

# local multiply with explicit transpose
loc <- t(prostate.local$AGE) %*% prostate.local$CAPSULE
expect_equal(1, dim(loc)[1])
expect_equal(1, dim(loc)[2])

# H2O multiply
remote <- t(prostate.hex$AGE) %*% prostate.hex$CAPSULE
expect_equal(1, dim(remote)[1])
expect_equal(1, dim(remote)[2])




}

h2oTest.doTest("PUB-860 we don't autotranspose vectors", test.pub_860_no_auto_transpose)

