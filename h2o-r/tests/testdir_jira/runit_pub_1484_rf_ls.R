setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pub.1484 <- function() {

    h2oTest.heading("BEGIN TEST")

    path = h2oTest.locate("smalldata/logreg/prostate.csv")
    prostate.hex = h2o.importFile(path, destination_frame="prostate.hex")
    h2o.ls()

    rf = h2o.randomForest(x=c(1,4), y="CAPSULE", training_frame=prostate.hex, ntrees=5)
    h2o.ls()

}

h2oTest.doTest("PUB-1484", test.pub.1484)
