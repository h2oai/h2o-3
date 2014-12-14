setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdocpredict.golden <- function(H2Oserver) {
	

prostate.hex = h2o.importURL(H2Oserver, path = "https://raw.github.com/0xdata/h2o/master/smalldata/logreg/prostate.csv", key = "prostate.hex")
prostate.glm = h2o.glm(y = "CAPSULE", x = c("AGE","RACE","PSA","DCAPS"), training_frame = prostate.hex, family="binomial", nfolds = 10, alpha = 0.5)
prostate.fit = predict(object = prostate.glm, newtraining_frame = prostate.hex)
prost <- summary(prostate.fit)


Log.info("Print output from as.data.frame call")
Log.info(paste("H2O Summary Prostate  :" ,prost))

testEnd()
}

doTest("R Doc Predict Example", test.rdocpredict.golden)

