setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.rdocRM.golden <- function() {

prosPath <- h2oTest.locate("smalldata/extdata/prostate.csv")
prostate.hex <- h2o.uploadFile(path = prosPath)
s <- as.h2o(runif(nrow(prostate.hex)))
prostate.hex <- prostate.hex[s <= 0.8,]
h2o.ls()
h2o.rm("Last.value.hex")
h2o.ls()
h2o.rm("prostate.hex")
remove(prostate.hex)
h2o.ls()


}

h2oTest.doTest("R Doc RM", test.rdocRM.golden)

