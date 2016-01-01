setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
##
# Testing glm picks correct link when unspecified: default canonical link for family
##




test <- function() {
    print("Reading in original prostate data.")
        prostate.data = h2o.uploadFile(h2oTest.locate("smalldata/prostate/prostate.csv.zip"), destination_frame="prostate.data", header=TRUE)

    print("Compare models with link unspecified and canonical link specified.")
    	print("GAUSSIAN: ") 
        model.gaussian.unspecified <- h2o.glm(x=c(2:8), y=9, training_frame=prostate.data, family="gaussian")
		model.gaussian.specified <- h2o.glm(x=c(2:8), y=9, training_frame=prostate.data, family="gaussian", link="identity")
		stopifnot(model.gaussian.unspecified@model$coefficients_table[1,]==model.gaussian.specified@model$coefficients_table[1,])

		print("BINOMIAL: ") 
        model.binomial.unspecified <- h2o.glm(x=c(3:9), y=2, training_frame=prostate.data, family="binomial")
		model.binomial.specified <- h2o.glm(x=c(3:9), y=2, training_frame=prostate.data, family="binomial", link="logit")
		stopifnot(model.binomial.unspecified@model$coefficients_table[1,]==model.binomial.specified@model$coefficients_table[1,])

		print("POISSON: ") 
        model.poisson.unspecified <- h2o.glm(x=c(3:9), y=2, training_frame=prostate.data, family="poisson")
		model.poisson.specified <- h2o.glm(x=c(3:9), y=2, training_frame=prostate.data, family="poisson", link="log")
		stopifnot(model.poisson.unspecified@model$coefficients_table[1,]==model.poisson.specified@model$coefficients_table[1,])

		print("GAMMA: ") 
        model.gamma.unspecified <- h2o.glm(x=c(4:9), y=3, training_frame=prostate.data, family="gamma")
		model.gamma.specified <- h2o.glm(x=c(4:9), y=3, training_frame=prostate.data, family="gamma", link="inverse")
		stopifnot(model.gamma.unspecified@model$coefficients_table[1,]==model.gamma.specified@model$coefficients_table[1,])

    
}

h2oTest.doTest("Testing glm picks correct link when unspecified: default canonical link for family", test)
