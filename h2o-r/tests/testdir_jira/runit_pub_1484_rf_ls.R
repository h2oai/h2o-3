setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

heading("BEGIN TEST")
#conn <- new("H2OConnection", ip=myIP, port=myPort)

path = locate("smalldata/logreg/prostate.csv")
prostate.hex = h2o.importFile( path, destination_frame="prostate.hex")
h2o.ls()

rf = h2o.randomForest(x=c(1,4), y="CAPSULE", training_frame=prostate.hex, ntrees=5)
h2o.ls()

PASS_BANNER()
