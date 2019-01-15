setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

stackedensemble.blending_frame.test <- function() {
    
}

doTest("Stacked Ensemble Blending Frame Test", stackedensemble.blending_frame.test)
