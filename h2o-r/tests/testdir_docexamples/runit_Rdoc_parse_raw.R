


test.rdoc_parse_raw.golden <- function() {


prosPath <- locate("smalldata/extdata/prostate.csv")
prostate.raw <- h2o.uploadFile(path = prosPath, parse = FALSE)
prostate.hex <- h2o.parseRaw(data = prostate.raw, destination_frame = "prostate.hex")


}

doTest("R Doc Parse Raw", test.rdoc_parse_raw.golden)

