setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# tests that infogram build the correct model for core infogram.  Make sure 
# 1. it gets the correct result compared to deep's code.
# 2. the relevance and cmi frame contains the correct values
# 3. models built with setting x = infogram model and x = admissible features yield the same result
infogramIris <- function() {
    bhexFV <- h2o.importFile(locate("smalldata/admissibleml_test/irisROriginal.csv"))
    bhexFV["Species"]<- h2o.asfactor(bhexFV["Species"])
    Y <- "Species"
    X <- c("Sepal.Length","Sepal.Width","Petal.Length","Petal.Width")
    deepRel <- sort(c(0.009010006, 0.011170417, 0.755170945, 1.000000000))
    deepCMI <- sort(c(0.1038524, 0.7135458, 0.5745915, 1.0000000))
    Log.info("Build the model")
    mFV <- h2o.infogram(y=Y, x=X, training_frame=bhexFV,  seed=12345, top_n_features=50)
    relCMIFrame <- mFV@admissible_score # get frames containing relevance and cmi
    frameCMI <- sort(as.vector(t(relCMIFrame[,5])))
    frameRel <- sort(as.vector(t(relCMIFrame[,4])))
    expect_equal(deepCMI, frameCMI, tolerance=1e-6) # check with Deep's result
    expect_equal(deepRel, frameRel, tolerance=1e-6) 
    # check model built with x=infogram return correct model
    model1 <- h2o.gbm(y=Y, x=mFV, training_frame=bhexFV,  seed=12345)
    logloss1 <- h2o.logloss(model1)
    model2 <- h2o.gbm(y=Y, x=mFV@admissible_features, training_frame=bhexFV, seed=12345)
    logloss2 <- h2o.logloss(model2)
    expect_true(abs(logloss1-logloss2) < 1e-6)
}

doTest("Infogram: Iris core infogram", infogramIris)
