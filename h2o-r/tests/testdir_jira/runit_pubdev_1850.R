setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



# Tests H2O's ability to read in numbers from the bit64 package
test.pubdev_1850 <- function() {

foo <- h2o.importFile(h2oTest.locate("smalldata/jira/hexdev_29.csv"), na.strings=list(NULL, c("fish","xyz"), NULL)) 
expect_equal(sum(is.na(foo)), 2)



}

h2oTest.doTest("PUBDEV-1850 Parse not setting NA strings properly", test.pubdev_1850)

