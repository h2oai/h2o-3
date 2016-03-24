setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
test.pubdev.2772 <- function(conn){
    m <- h2o.gbm(1:4,5,training_frame=as.h2o(iris)[2:148,],validation_frame=as.h2o(iris)[51:100,],nfolds=5)
}

doTest("PUBDEV-2772", test.pubdev.2772)
