setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdocanomaly.golden <- function() {
prosPath <- system.file("extdata", "prostate.csv", package="h2o")
prostate.hex <- h2o.uploadFile(path = prosPath)
prostate.dl <- h2o.deeplearning(x = 3:9, y = 2, training_frame = prostate.hex, autoencoder = TRUE)
prostate.anon <- h2o.anomaly(prostate.hex, prostate.dl)
head(prostate.anon)

testEnd()
}

doTest("R Doc Anomaly", test.rdocanomaly.golden)

