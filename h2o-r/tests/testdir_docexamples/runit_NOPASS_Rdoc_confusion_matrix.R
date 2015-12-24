setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.rdocclusterstatus.golden <- function() {


prosPath = system.file("extdata", "prostate.csv", package="h2o")
prostate.hex = h2o.uploadFile( path = prosPath)
prostate.gbm = h2o.gbm(x = 3:9, y = 2, training_frame = prostate.hex, loss = "bernoulli")
prostate.pred = predict(prostate.gbm)
h2o.table(prostate.pred[,1], prostate.hex[,2])

}

doTest("R Doc Cluster Status", test.rdocclusterstatus.golden)

