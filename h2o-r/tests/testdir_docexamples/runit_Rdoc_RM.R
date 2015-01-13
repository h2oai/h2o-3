setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdocRM.golden <- function(H2Oserver) {

prosPath <- system.file("extdata", "prostate.csv", package="h2o")
prostate.hex <- h2o.importFile(H2Oserver, path = prosPath)
s <- as.h2o(H2Oserver, runif(nrow(prostate.hex)))
prostate.hex <- prostate.hex[s <= 0.8,]
h2o.ls()
h2o.rm(conn= H2Oserver, keys= "Last.value.hex")
h2o.ls(H2Oserver)
h2o.rm(conn= H2Oserver, keys= "prostate.hex")
remove(prostate.hex)
h2o.ls(H2Oserver)

testEnd()
}

doTest("R Doc RM", test.rdocRM.golden)

