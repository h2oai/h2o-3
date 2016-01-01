setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.rdocclusterstatus.golden <- function() {

    prosPath <- h2oTest.locate("smalldata/extdata/prostate.csv")
    hex <- h2o.uploadFile(prosPath)
    hex[,2] <- as.factor(hex[,2])
    model <- h2o.gbm(x = 3:9, y = 2, training_frame = hex, distribution = "bernoulli")
    h2o.confusionMatrix(model, hex)
    # Generating a ModelMetrics object
    perf <- h2o.performance(model, hex)
    h2o.confusionMatrix(perf)

    
}

h2oTest.doTest("R Doc Cluster Status", test.rdocclusterstatus.golden)

