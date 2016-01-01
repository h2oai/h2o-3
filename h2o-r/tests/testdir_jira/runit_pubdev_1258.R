setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
####
### Test offset implementation in R -https://0xdata.atlassian.net/browse/PUBDEV-1258
####




test.GLM.offset <- function(){

    h2oTest.logInfo("Importing prostate dataset...")
    prostate.hex <- h2o.importFile(h2oTest.locate("smalldata/prostate/prostate.csv"))


    h2oTest.logInfo("Run glm with offset specified")
    my_glm <- h2o.glm(x = 1:3, y = 4, training_frame = prostate.hex, family = "gaussian", offset_column = "GLEASON")

    
}

h2oTest.doTest("GLM offset implementation test", test.GLM.offset)
