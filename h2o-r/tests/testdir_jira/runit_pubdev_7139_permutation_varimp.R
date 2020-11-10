setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

library(h2o)
h2o.init()

Log.info("Creating GBM model...")
prosPath <- h2o:::.h2o.locate("smalldata/logreg/prostate.csv")
prostate.hex <- h2o.uploadFile(path = prosPath, destination_frame = "prostate.hex")

prostate.gbm <- h2o.gbm(x = setdiff(colnames(prostate.hex), "CAPSULE"), y = "CAPSULE", training_frame = prostate.hex, ntrees = 10, max_depth = 5, learn_rate = 0.1, distribution = "bernoulli")

Log.info("Calculating Permutation Variable Importance...")
permutation_varimp <- h2o.permutation_varimp(iris.gbm, prostate.hex)
Log.info(paste0("Permutation Variable importance: ",permutation_varimp))
