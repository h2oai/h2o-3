setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
####
### Test offset implementation in R - https://github.com/h2oai/h2o-3/issues/14236
####




test.GLM.offset <- function(){

    Log.info("Importing prostate dataset...")
    prostate.hex <- h2o.importFile(locate("smalldata/prostate/prostate.csv"))


    Log.info("Run glm with offset specified")
    my_glm <- h2o.glm(x = 1:3, y = 4, training_frame = prostate.hex, family = "gaussian", offset_column = "GLEASON")

    
}

doTest("GLM offset implementation test", test.GLM.offset)
