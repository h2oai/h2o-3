setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# make sure anovaGlm na handling is working for meanImputation
test.model.anovaglm.mean.imputation <- function() {
    train <- h2o.importFile(path = locate("smalldata/prostate/prostate_complete.csv.zip"))
    train[1,4] <- NaN
    train[10,9] <- NaN
    model1 <- h2o.anovaglm(y = 'CAPSULE', x = c('AGE','VOL','DCAPS'),  training_frame = train, 
                           family = "binomial", missing_values_handling="MeanImputation")
     # na fill with column mean manually   
    ageMean = 66.05851613674022
    volMean = 15.884949535351229
    train[1,4] <- ageMean
    train[10,9] <- volMean
    model2 <- h2o.anovaglm(y = 'CAPSULE', x = c('AGE','VOL','DCAPS'),  training_frame = train, 
                               family = "binomial")
    # check coefficients are the same
    coefs1 <- model1@model$coefficients
    coefs2 <- model2@model$coefficients
    expect_equal(coefs1, coefs2)
}

doTest("ANOVA GLM mean imputation with binomial family", test.model.anovaglm.mean.imputation)
