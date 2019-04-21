setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.rdocanomaly.golden <- function() {
    e <- tryCatch({   # deeplearning will throw an error when an unstable model is built
    prosPath = locate("smalldata/extdata/prostate.csv")
    prostate.hex = h2o.importFile(path = prosPath)
    prostate.dl = h2o.deeplearning(x = 3:9, training_frame = prostate.hex, autoencoder = TRUE,
                                hidden = c(10, 10), epochs = 5)
    prostate.anon = h2o.anomaly(prostate.dl, prostate.hex)
    head(prostate.anon)
    prostate.anon.per.feature = h2o.anomaly(prostate.dl, prostate.hex, per_feature=TRUE)
    head(prostate.anon.per.feature)}, error = function(x) x)

    if (!is.null(e) && (typeof(e) != "list") && (!all(sapply("DistributedException", grepl, e[[1]])))) {
      FAIL(e)   # throw error unless it is unstable model error.
    }
}

doTest("R Doc Anomaly", test.rdocanomaly.golden)

