setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.rdocRM.golden <- function() {

prosPath <- system.file("extdata", "prostate.csv", package="h2o")
prostate.hex <- h2o.uploadFile( path = prosPath)
s <- as.h2o( runif(nrow(prostate.hex)))
prostate.hex <- prostate.hex[s <= 0.8,]
h2o.ls()
h2o.rm(conn= H2Oserver, ids = "Last.value.hex")
h2o.ls(H2Oserver)
h2o.rm(conn= H2Oserver, ids = "prostate.hex")
remove(prostate.hex)
h2o.ls(H2Oserver)

}

doTest("R Doc RM", test.rdocRM.golden)

