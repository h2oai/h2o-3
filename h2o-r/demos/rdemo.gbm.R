library(h2o)
h2o.init()

prosPath <- h2o:::.h2o.locate("smalldata/logreg/prostate.csv")
print(paste("Uploading", prosPath))
prostate.hex <- h2o.uploadFile(path = prosPath, destination_frame = "prostate.hex")

print("Print out summary of prostate.csv")
prostate.hex$CAPSULE <- as.factor(prostate.hex$CAPSULE)
print(summary(prostate.hex))

myX <- setdiff(colnames(prostate.hex), "CAPSULE")
print(paste("Run GBM with y = CAPSULE, x =", paste(myX, collapse=",")))
prostate.gbm <- h2o.gbm(x = setdiff(colnames(prostate.hex), "CAPSULE"), y = "CAPSULE", training_frame = prostate.hex, ntrees = 10, max_depth = 5, learn_rate = 0.1, distribution = "bernoulli")
print(prostate.gbm)

print("Run GBM with y = CAPSULE, x = AGE, RACE, PSA, VOL, GLEASON")
prostate.gbm2 <- h2o.gbm(x = c("AGE", "RACE", "PSA", "VOL", "GLEASON"), y = "CAPSULE", training_frame = prostate.hex, ntrees = 10, max_depth = 8, min_rows = 10, learn_rate = 0.2, distribution = "bernoulli")
print(prostate.gbm2)

irisPath <- h2o:::.h2o.locate("smalldata/iris/iris.csv")
print(paste("Uploading", irisPath))
iris.hex <- h2o.uploadFile(path = irisPath, destination_frame = "iris.hex")

print("Print out summary of iris.csv")
print(summary(iris.hex))

print("Run GBM with y = column 5, x = columns 1:4")
iris.gbm <- h2o.gbm(x = 1:4, y = 5, training_frame = iris.hex, distribution = "multinomial")
print(iris.gbm)