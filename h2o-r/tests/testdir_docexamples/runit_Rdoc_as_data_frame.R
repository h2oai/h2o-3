setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdocasdataframe.golden <- function() {

    prosPath <- system.file("extdata", "prostate.csv", package="h2o")
    prostate.hex <- h2o.uploadFile(path = prosPath)
    as.data.frame.Frame(prostate.hex)

    testEnd()
}

doTest("R Doc as.data.frame", test.rdocasdataframe.golden)

