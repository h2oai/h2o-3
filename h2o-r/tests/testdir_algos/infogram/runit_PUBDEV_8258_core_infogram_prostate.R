setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# simple to check to make sure prostate example runs.  It is okay for this test to not have
# any kind of expect_true statements
infogramProstate <- function() {
    prostate<- h2o.uploadFile(locate("smalldata/logreg/prostate_train.csv"))
    prostate$CAPSULE <- as.factor(prostate$CAPSULE)  
    infogramModel <- h2o.infogram(y="CAPSULE", x=c("RACE", "AGE", "PSA", "DCAPS"), training_frame=prostate)
}

doTest("Infogram: Prostate core infogram", infogramProstate)
