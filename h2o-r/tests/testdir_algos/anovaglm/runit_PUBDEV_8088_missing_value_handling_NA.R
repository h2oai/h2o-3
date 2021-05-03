setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# make sure anovaGlm na handling is working for meanImputation
test.model.anovaglm.mean.imputation <- function() {
    train <- h2o.importFile(path = locate("smalldata/prostate/prostate_complete.csv.zip"))
    train[1,4] <- NaN
    train[10,9] <- NaN
    aModel <- h2o.anovaglm(y = 'CAPSULE', x = c('AGE','VOL','DCAPS'),  training_frame = train, 
                           family = "binomial", missing_values_handling="MeanImputation")
     # na fill with column mean manually   
    ageMean = 66.05851613674022
    volMean = 15.884949535351229
    train[1,4] <- ageMean
    train[10,9] <- volMean
    aModelMean <- h2o.anovaglm(y = 'CAPSULE', x = c('AGE','VOL','DCAPS'),  training_frame = train, 
                               family = "binomial")
    # check coefficients are the same
    coeffsTable <- aModel@model$coefficients
    coeffsTableMean <- aModelMean@model$coefficients
    expect_equal(coeffsTable, coeffsTableMean)
}

doTest("AnovaGLM mean imputation with binomial family", test.model.anovaglm.mean.imputation)
