setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

library(h2o)
h2o.init()

Log.info("Creating GBM model...")
prosPath <- h2o:::.h2o.locate("smalldata/logreg/prostate.csv")
prostate.hex <- h2o.uploadFile(path = prosPath, destination_frame = "prostate.hex")
prostate.hex$CAPSULE <- as.factor(prostate.hex$CAPSULE)

myX <- setdiff(colnames(prostate.hex), "CAPSULE")
prostate.gbm <- h2o.gbm(x = setdiff(colnames(prostate.hex), "CAPSULE"), y = "CAPSULE", training_frame = prostate.hex, ntrees = 10, max_depth = 5, learn_rate = 0.1, distribution = "bernoulli")

prostate.gbm2 <- h2o.gbm(x = c("AGE", "RACE", "PSA", "VOL", "GLEASON"), y = "CAPSULE", training_frame = prostate.hex, ntrees = 10, max_depth = 8, min_rows = 10, learn_rate = 0.2, distribution = "bernoulli")

irisPath <- h2o:::.h2o.locate("smalldata/iris/iris.csv")
iris.hex <- h2o.uploadFile(path = irisPath, destination_frame = "iris.hex")

iris.gbm <- h2o.gbm(x = 1:4, y = 5, training_frame = iris.hex, distribution = "multinomial")

Log.info("Calculating Permutation Variable Importance...")
permutation_varimp <- h2o.permutation_varimp(iris.gbm, prostate.hex)
Log.info(paste0("Permutation Variable importance: ",permutation_varimp))
