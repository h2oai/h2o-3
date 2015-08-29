setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdocRM.golden <- function() {

prosPath <- system.file("extdata", "prostate.csv", package="h2o")
prostate.hex <- h2o.uploadFile(path = prosPath)
s <- as.h2o(runif(nrow(prostate.hex)))
prostate.hex <- prostate.hex[s <= 0.8,]
h2o.ls()
h2o.rm(ids = "Last.value.hex")
h2o.ls()
h2o.rm(
ids = "prostate.hex")
remove(prostate.hex)
h2o.ls()

testEnd()
}

doTest("R Doc RM", test.rdocRM.golden)

