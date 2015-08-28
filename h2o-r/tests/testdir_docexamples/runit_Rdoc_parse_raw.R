setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdoc_parse_raw.golden <- function(H2Oserver) {


prosPath <- system.file("extdata", "prostate.csv", package="h2o")
prostate.raw <- h2o.uploadFile(path = prosPath, parse = FALSE)
prostate.hex <- h2o.parseRaw(data = prostate.raw, destination_frame = "prostate.hex")

testEnd()
}

doTest("R Doc Parse Raw", test.rdoc_parse_raw.golden)

