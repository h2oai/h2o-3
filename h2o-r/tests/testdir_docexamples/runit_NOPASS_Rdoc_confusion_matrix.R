setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdocclusterstatus.golden <- function() {

    prosPath <- system.file("extdata", "prostate.csv", package="h2o")
    hex <- h2o.uploadFile(prosPath)
    hex[,2] <- as.factor(hex[,2])
    model <- h2o.gbm(x = 3:9, y = 2, training_frame = hex, distribution = "bernoulli")
    h2o.confusionMatrix(model, hex)
    # Generating a ModelMetrics object
    perf <- h2o.performance(model, hex)
    h2o.confusionMatrix(perf)

    
}

doTest("R Doc Cluster Status", test.rdocclusterstatus.golden)

