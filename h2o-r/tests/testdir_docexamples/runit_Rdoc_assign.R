setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.h2o.assign.golden <- function() {


prosPath <- h2oTest.locate("smalldata/extdata/prostate.csv")
prostate.hex <- h2o.uploadFile(path = prosPath)
prostate.qs <- quantile(prostate.hex$PSA)
PSA.outliers <- prostate.hex[prostate.hex$PSA <= prostate.qs[2] | prostate.hex$PSA >= prostate.qs[10],]
PSA.outliers <- h2o.assign(PSA.outliers, "PSA.outliers")
head(prostate.hex)
head(PSA.outliers)


}

h2oTest.doTest("R Doc h2o.assign", test.h2o.assign.golden)
