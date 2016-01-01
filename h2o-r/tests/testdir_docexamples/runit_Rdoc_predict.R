setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.rdocpredict.golden <- function() {


prostate.hex <- h2o.uploadFile(path = h2oTest.locate("smalldata/logreg/prostate.csv"), destination_frame = "prostate.hex")

# nfolds is currently unsupported
prostate.glm <- h2o.glm(y = "CAPSULE", x = c("AGE","RACE","PSA","DCAPS"), training_frame = prostate.hex, family="binomial", alpha = 0.5)
# prostate.glm <- h2o.glm(y = "CAPSULE", x = c("AGE","RACE","PSA","DCAPS"), training_frame = prostate.hex, family="binomial", nfolds = 10, alpha = 0.5)
prostate.fit <- predict(object = prostate.glm, newdata = prostate.hex)
prost <- summary(prostate.fit)


h2oTest.logInfo("Print output from as.data.frame call")
h2oTest.logInfo(paste("H2O Summary Prostate  :" ,prost))


}

h2oTest.doTest("R Doc Predict Example", test.rdocpredict.golden)

