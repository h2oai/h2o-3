##
# Testing glm throws error when the link specified is incompatible with the family
##

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test <- function() {
    print("Reading in original prostate data.")
        prostate.data = h2o.uploadFile(locate("smalldata/prostate/prostate.csv.zip"), destination_frame="prostate.data", header=TRUE)

    print("Throw error when trying to create model with incompatible logit link.")
        assertError(h2o.model <- h2o.glm(x=c(2:8), y=9, training_frame=prostate.data, family="gaussian", link="logit"))
	assertError(h2o.model <- h2o.glm(x=c(2:8), y=9, training_frame_frame=prostate.data, family="tweedie", link="log"))
	assertError(h2o.model <- h2o.glm(x=c(3:9), y=2, training_frame=prostate.data, family="binomial", link="inverse"))
    
    testEnd()
}

doTest("Testing glm throws error when the link specified is incompatible with the family.", test)
