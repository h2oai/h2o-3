####
### Test offset implementation in R -https://0xdata.atlassian.net/browse/PUBDEV-1258
####
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')


test.GLM.offset <- function(){

    Log.info("Importing prostate dataset...")
    prostate.hex <- h2o.importFile(locate("smalldata/prostate/prostate.csv"))


    Log.info("Run glm with offset specified")
    my_glm <- h2o.glm(x = 1:3, y = 4, training_frame = prostate.hex, family = "gaussian", offset_column = "GLEASON")

    testEnd()
}

doTest("GLM offset implementation test", test.GLM.offset)
