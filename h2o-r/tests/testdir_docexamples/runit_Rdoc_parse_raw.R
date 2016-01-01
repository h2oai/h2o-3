setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.rdoc_parse_raw.golden <- function() {


prosPath <- h2oTest.locate("smalldata/extdata/prostate.csv")
prostate.raw <- h2o.uploadFile(path = prosPath, parse = FALSE)
prostate.hex <- h2o.parseRaw(data = prostate.raw, destination_frame = "prostate.hex")


}

h2oTest.doTest("R Doc Parse Raw", test.rdoc_parse_raw.golden)

