setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# make sure tests that do not specify family runs to completion.
test.model.anovaglm.prostate <- function() {
    prostate <- h2o.importFile(path = locate("smalldata/prostate/prostate.csv"))
    prostate$CAPSULE <- h2o.asfactor(prostate$CAPSULE)
    # check for classification
    modelBinomial <- h2o.anovaglm(y = "CAPSULE", x = c("AGE","VOL","DCAPS"), training_frame = prostate, family="binomial")
    logloss <- h2o.logloss(modelBinomial)
    modelBinomial2 <- h2o.anovaglm(y = "CAPSULE", x = c("AGE","VOL","DCAPS"), training_frame = prostate, family="binomial")
    logloss2 <- h2o.logloss(modelBinomial2)
    expect_true(abs(logloss-logloss2) < 1e-6)
    # check for regression
    model <- h2o.anovaglm(y = "VOL", x = c("CAPSULE", "RACE"), training_frame = prostate)
    mse <- h2o.mse(model)
    model2 <- h2o.anovaglm(y = "VOL", x = c("CAPSULE", "RACE"), training_frame = prostate, family = "gaussian")
    mse2 <- h2o.mse(model)
    expect_true(abs(mse-mse2) < 1e-6)
}

doTest("ANOVA GLM run with prostate data", test.model.anovaglm.prostate)
