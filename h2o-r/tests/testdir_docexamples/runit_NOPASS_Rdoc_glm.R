setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdocglm.golden <- function(H2Oserver) {
	


prostate.hex <- h2o.importURL(path = locate("smalldata/logreg/prostate.csv"), destination_frame = "prostate.hex")
h2o.glm(y = "CAPSULE", x = c("AGE","RACE","PSA","DCAPS"), training_frame = prostate.hex, family = "binomial", nfolds = 10, alpha = 0.5)
myX <- setdiff(colnames(prostate.hex), c("ID", "DPROS", "DCAPS", "VOL"))
h2o.glm(y = "VOL", x = myX, training_frame = prostate.hex, family = "gaussian", nfolds = 5, alpha = 0.1)

airlines.hex <-  h2o.importURL(path = locate("smalldata/airlines/AirlinesTrain.csv.zip"))
h2o.glm(x = c('Distance', 'Origin', 'Dest', 'UniqueCarrier'), y = 'IsDepDelayed', family = 'binomial', training_frame = airlines.hex)

testEnd()
}

doTest("R Doc GLM example", test.rdocglm.golden)

