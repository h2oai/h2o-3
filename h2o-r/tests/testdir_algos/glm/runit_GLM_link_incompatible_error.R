##
# Testing glm throws error when the link specified is incompatible with the family
##

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../findNSourceUtils.R')

test <- function(conn) {
    print("Reading in original prostate data.")
        prostate.data = h2o.uploadFile(conn, locate("smalldata/prostate/prostate.csv.zip"), key="prostate.data", header=TRUE)

    print("Throw error when trying to create model with incompatible logit link.")
        assertError(h2o.model <- h2o.glm(x=c(2:8), y=9, data=prostate.data, family="gaussian", link="logit"))
		assertError(h2o.model <- h2o.glm(x=c(2:8), y=9, data=prostate.data, family="tweedie", link="log"))
		assertError(h2o.model <- h2o.glm(x=c(3:9), y=2, data=prostate.data, family="binomial", link="inverse"))
    
    testEnd()
}

doTest("Testing glm throws error when the link specified is incompatible with the family.", test)
