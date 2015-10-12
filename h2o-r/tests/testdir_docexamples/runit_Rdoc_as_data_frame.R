setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdocasdataframe.golden <- function() {

    prosPath <- locate("smalldata/extdata/prostate.csv")
    prostate.hex <- h2o.uploadFile(path = prosPath)
    as.data.frame.Frame(prostate.hex)

    
}

doTest("R Doc as.data.frame", test.rdocasdataframe.golden)

