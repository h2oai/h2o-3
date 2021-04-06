setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# Make sure infogram works with glm
infogramIrisGLM <- function() {
    bhexFV <- h2o.importFile(locate("smalldata/admissibleml_test/irisROriginal.csv"))
    bhexFV["Species"]<- h2o.asfactor(bhexFV["Species"])
    Y <- "Species"
    X <- c("Sepal.Length","Sepal.Width","Petal.Length","Petal.Width")
    Log.info("Build the model")
    mFV <- h2o.infogram(y=Y, x=X, training_frame=bhexFV,  seed=12345, top_n_features=50, distribution="multinomial", algorithm="glm")
 
    # check model built with x=infogram return correct model
    model1 <- h2o.glm(y=Y, x=mFV, training_frame=bhexFV,  seed=12345, family="multinomial")
    logloss1 <- h2o.logloss(model1)
    model2 <- h2o.glm(y=Y, x=mFV@admissible_features, training_frame=bhexFV, seed=12345, family="multinomial")
    logloss2 <- h2o.logloss(model2)
    expect_true(abs(logloss1-logloss2) < 1e-6)
}

doTest("Infogram: Iris core infogram using GLM", infogramIrisGLM)
