setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdocasdataframe.golden <- function(H2Oserver) {

    prosPath <- system.file("extdata", "prostate.csv", package="h2o")
    prostate.hex <- h2o.importFile(H2Oserver, path = prosPath)
    as.data.frame.H2OFrame(prostate.hex)

    testEnd()
}

doTest("R Doc as.data.frame", test.rdocasdataframe.golden)

