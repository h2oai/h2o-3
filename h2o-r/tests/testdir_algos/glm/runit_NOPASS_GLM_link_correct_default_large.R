##
# Testing glm picks correct link when unspecified: default canonical link for family
##

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test <- function(conn) {
    print("Reading in original prostate data.")
        prostate.data = h2o.uploadFile(conn, locate("smalldata/prostate/prostate.csv.zip"), key="prostate.data", header=TRUE)

    print("Compare models with link unspecified and canonical link specified.")
    	print("GAUSSIAN: ") 
        model.gaussian.unspecified <- h2o.glm(x=c(2:8), y=9, training_frame=prostate.data, family="gaussian")
		model.gaussian.specified <- h2o.glm(x=c(2:8), y=9, training_frame=prostate.data, family="gaussian", link="identity")
		stopifnot(model.gaussian.unspecified@model$coefficients==model.gaussian.specified@model$coefficients)

		print("BINOMIAL: ") 
        model.binomial.unspecified <- h2o.glm(x=c(3:9), y=2, training_frame=prostate.data, family="binomial")
		model.binomial.specified <- h2o.glm(x=c(3:9), y=2, training_frame=prostate.data, family="binomial", link="logit")
		stopifnot(model.binomial.unspecified@model$coefficients==model.binomial.specified@model$coefficients)

		print("POISSON: ") 
        model.poisson.unspecified <- h2o.glm(x=c(3:9), y=2, training_frame=prostate.data, family="poisson")
		model.poisson.specified <- h2o.glm(x=c(3:9), y=2, training_frame=prostate.data, family="poisson", link="log")
		stopifnot(model.poisson.unspecified@model$coefficients==model.poisson.specified@model$coefficients)

		print("GAMMA: ") 
        model.gamma.unspecified <- h2o.glm(x=c(4:9), y=3, training_frame=prostate.data, family="gamma")
		model.gamma.specified <- h2o.glm(x=c(4:9), y=3, training_frame=prostate.data, family="gamma", link="inverse")
		stopifnot(model.gamma.unspecified@model$coefficients==model.gamma.specified@model$coefficients)

		print("TWEEDIE: ") 
        model.tweedie.unspecified <- h2o.glm(x=c(2:8), y=9, training_frame=prostate.data, family="tweedie")
		model.tweedie.specified <- h2o.glm(x=c(2:8), y=9, training_frame=prostate.data, family="tweedie", link="tweedie")
		stopifnot(model.tweedie.unspecified@model$coefficients==model.tweedie.specified@model$coefficients)

    testEnd()
}

doTest("Testing glm picks correct link when unspecified: default canonical link for family", test)
