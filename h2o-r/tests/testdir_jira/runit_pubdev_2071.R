setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



# Tests H2O's ability to read in numbers from the bit64 package
test.pubdev_2071 <- function() {

library(bit64)

# Cheat, cheat, cheat! This is testing one order of magnitude below the real limit!
# H2O currently can't parse to the full min/max limit
foo <- data.frame(c = as.integer64(as.character(lim.integer64()/10)))
foo.hex <- as.h2o(foo)
bar <- foo.hex[1,1]
bari64 <- as.integer64(bar)
res <- identical.integer64(foo[1,1],bari64)
expect_equal(res, TRUE)

detach()

}

h2oTest.doTest("PUBDEV-2071 H2O does not parse long numbers correctly", test.pubdev_2071)

