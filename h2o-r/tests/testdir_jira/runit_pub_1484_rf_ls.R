setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.pub.1484 <- function() {

    heading("BEGIN TEST")

    path = locate("smalldata/logreg/prostate.csv")
    prostate.hex = h2o.importFile(path, destination_frame="prostate.hex")
    h2o.ls()

    rf = h2o.randomForest(x=c(1,4), y="CAPSULE", training_frame=prostate.hex, ntrees=5)
    h2o.ls()

}

doTest("PUB-1484", test.pub.1484)
