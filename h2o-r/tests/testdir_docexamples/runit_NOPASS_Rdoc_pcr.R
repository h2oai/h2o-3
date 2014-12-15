setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.RdocPCR.golden <- function(H2Oserver) {
	

prostate.hex <- h2o.importURL(H2Oserver, path = "smalldata/logreg/prostate.csv", key = "prostate.hex")
h2o.pcr(x = c("AGE","RACE","PSA","DCAPS"), y = "CAPSULE", data = prostate.hex, family = "binomial", nfolds = 10, alpha = 0.5, ncomp = 3)

testEnd()
}

doTest("R Doc PCR", test.RdocPCR.golden)

