setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")




test.rdocquantiles.golden <- function() {

prosPath <- h2oTest.locate("smalldata/extdata/prostate.csv")
prostate.hex <- h2o.uploadFile(path = prosPath)
quantile(prostate.hex[,3])
for(i in 1:ncol(prostate.hex))
  quantile(prostate.hex[,i])


}

h2oTest.doTest("R Doc Quantiles", test.rdocquantiles.golden)

