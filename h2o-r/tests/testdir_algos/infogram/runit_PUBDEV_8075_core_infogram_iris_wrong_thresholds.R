setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# tests that infogram generate warnings when wrong thresholds are set for core infogram
infogramIrisWrongThresholds <- function() {
    bhexFV <- h2o.importFile(locate("smalldata/admissibleml_test/irisROriginal.csv"))
    bhexFV["Species"]<- h2o.asfactor(bhexFV["Species"])
    Y <- "Species"
    X <- c("Sepal.Length","Sepal.Width","Petal.Length","Petal.Width")
    Log.info("Build the model")
    expect_warning(h2o.infogram(y=Y, x=X, training_frame=bhexFV, safety_index_threshold=0.2))
    expect_warning(h2o.infogram(y=Y, x=X, training_frame=bhexFV, relevance_index_threshold=0.2))
}

doTest("Infogram warning: Iris core infogram with wrong thresholds being set", infogramIrisWrongThresholds)
