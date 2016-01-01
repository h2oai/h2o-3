setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.rdocanomaly.golden <- function() {

    prosPath = h2oTest.locate("smalldata/extdata/prostate.csv")
    prostate.hex = h2o.importFile(path = prosPath)
    prostate.dl = h2o.deeplearning(x = 3:9, training_frame = prostate.hex, autoencoder = TRUE,
                                hidden = c(10, 10), epochs = 5)
    prostate.anon = h2o.anomaly(prostate.dl, prostate.hex)
    head(prostate.anon)
    prostate.anon.per.feature = h2o.anomaly(prostate.dl, prostate.hex, per_feature=TRUE)
    head(prostate.anon.per.feature)

    
}

h2oTest.doTest("R Doc Anomaly", test.rdocanomaly.golden)

