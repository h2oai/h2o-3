setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pub_507_parse_fail <- function() {

hex <- h2o.importFile(normalizePath(h2oTest.locate("smalldata/jira/pub_507.csv")), "p507")

rdat <- read.csv(normalizePath(h2oTest.locate("smalldata/jira/pub_507.csv")))

h2oTest.logInfo("The data that R read in.")
print(rdat)

h2oTest.logInfo("The data that H2O read in.")
print(hex)

expect_equal(as.data.frame(hex[2,1])[1,1], rdat[2,1])



}

h2oTest.doTest("PUB-507 H2O does not parse numbers correctly", test.pub_507_parse_fail)

