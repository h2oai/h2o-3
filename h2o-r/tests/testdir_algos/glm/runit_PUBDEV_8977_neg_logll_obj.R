setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test_negll_obj <- function() {
    prostate_h2o <- h2o.importFile(locate("smalldata/prostate/prostate.csv"))
    x=c("AGE","RACE","DPROS","DCAPS","PSA","GLEASON","CAPSULE")
    model <- h2o.glm(x = x, y="VOL", training_frame=prostate_h2o, generate_scoring_history=TRUE)
    modelNoReg <- h2o.glm(x = x, y="VOL", training_frame=prostate_h2o,lambda=0.0, generate_scoring_history=TRUE)
    nll <- h2o.negative_log_likelihood(model)
    nllNoReg <- h2o.negative_log_likelihood(modelNoReg)
    obj <- h2o.average_objective(model)
    objNoReg <- h2o.average_objective(modelNoReg)
    expect_true(abs(nll-obj/model@allparameters$obj_reg) > 1e-6)
    expect_true(abs(nllNoReg-objNoReg/modelNoReg@allparameters$obj_reg) < 1e-6)
}

doTest("GLM test access to negative log likelihood and average objective function values", test_negll_obj)
