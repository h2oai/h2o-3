setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.glm2Poissonregression.golden <- function() {
	
#Import data: 
h2oTest.logInfo("Importing CUSE data...") 
cuseH2O<- h2o.uploadFile(h2oTest.locate("smalldata/logreg/cuseexpanded.csv"), destination_frame="cuseH2O")
cuseR<- read.csv(h2oTest.locate("smalldata/logreg/cuseexpanded.csv"), header=T)

h2oTest.logInfo("Test H2O Poisson not regularized")
h2oTest.logInfo("Run matching models in R and H2O")
fitH2O<- h2o.glm(y="Using", x=c("Age", "Ed", "Wantsmore"), training_frame=cuseH2O, family="poisson", lambda=0, alpha=0, nfolds=0)
fitR<- glm(Using ~ AgeA + AgeC + AgeD + LowEd + MoreYes, family=poisson, data=cuseR)


h2oTest.logInfo("Print model statistics for R and H2O... \n")
h2oTest.logInfo(paste("H2O Deviance  : ", fitH2O@model$training_metrics@metrics$residual_deviance,      "\t\t", "R Deviance   : ", fitR$deviance))
h2oTest.logInfo(paste("H2O Null Dev  : ", fitH2O@model$training_metrics@metrics$null_deviance, "\t\t", "R Null Dev   : ", fitR$null.deviance))
h2oTest.logInfo(paste("H2O residul df: ", fitH2O@model$training_metrics@metrics$residual_degrees_of_freedom,    "\t\t\t\t", "R residual df: ", fitR$df.residual))
h2oTest.logInfo(paste("H2O null df   : ", fitH2O@model$training_metrics@metrics$null_degrees_of_freedom,       "\t\t\t\t", "R null df    : ", fitR$df.null))
h2oTest.logInfo(paste("H2O AIC       : ", fitH2O@model$training_metrics@metrics$AIC,           "\t\t", "R AIC        : ", fitR$aic))

h2oTest.logInfo("Compare model statistics in R to model statistics in H2O")
expect_equal(fitH2O@model$training_metrics@metrics$null_deviance, fitR$null.deviance, tolerance = 0.01)
expect_equal(fitH2O@model$training_metrics@metrics$residual_deviance, fitR$deviance, tolerance = 0.01)
expect_equal(fitH2O@model$training_metrics@metrics$residual_degrees_of_freedom, fitR$df.residual, tolerance = 0.01)
expect_equal(fitH2O@model$training_metrics@metrics$null_degrees_of_freedom, fitR$df.null, tolerance = 0.01)
expect_equal(fitH2O@model$training_metrics@metrics$AIC, fitR$aic, tolerance = 0.01)


}

h2oTest.doTest("GLM Test: Poisson Non Regularized", test.glm2Poissonregression.golden)

