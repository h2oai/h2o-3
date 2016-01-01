setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.glmn1NullDev.golden <- function() {

airlines.hex <-  h2o.importFile(path = h2oTest.locate("smalldata/airlines/AirlinesTrain.csv.zip"))
fitH2O <- h2o.glm(x = c('Distance', 'Origin', 'Dest', 'UniqueCarrier'), y = 'IsDepDelayed', family = 'binomial', training_frame = airlines.hex)

#Print deviances make sure we're returning a number
h2oTest.logInfo("Print model statistics for R and H2O... \n")
h2oTest.logInfo(paste("H2O Deviance  : ", fitH2O@model$training_metrics@metrics$residual_deviance))
h2oTest.logInfo(paste("H2O Null Dev  : ", fitH2O@model$training_metrics@metrics$null_deviance))
expect_false(fitH2O@model$training_metrics@metrics$null_deviance=="NaN")

}

h2oTest.doTest("GLM Null Dev is numeric", test.glmn1NullDev.golden)

