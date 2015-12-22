setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.rdocsd.golden <- function(localH2O) {

    prosPath <- system.file("extdata", "prostate.csv", package="h2o")
    prostate.hex <- h2o.uploadFile(localH2O, path = prosPath)
    sd(prostate.hex$AGE)

}

doTest("R Doc SD", test.rdocsd.golden)

