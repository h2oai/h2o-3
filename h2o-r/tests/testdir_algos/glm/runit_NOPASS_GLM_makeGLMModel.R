### This tests offsets in glm ######
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test <- function(h) {
    prostate_h2o <- h2o.importFile(locate("smalldata/prostate/prostate.csv"))

    model <- h2o.glm(x=c("AGE","DPROS","DCAPS","PSA","VOL","GLEASON"), y="CAPSULE", training_frame=prostate_h2o,
                     family="gaussian", alpha=0)

    new_beta <- c(0.5, 0.5, 0.5, 0.5, 0.5, 0.5)
    names(new_beta) <- c("AGE","DPROS","DCAPS","PSA","VOL","GLEASON")

    new_glm <- h2o.makeGLMModel(model, new_beta)

    testEnd()
}

doTest("GLM makeGLMModel", test)