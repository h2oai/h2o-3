setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.glmn1NullDev.golden <- function() {

airlines.hex <-  h2o.importFile(path = locate("smalldata/airlines/AirlinesTrain.csv.zip"))
fitH2O <- h2o.glm(x = c('Distance', 'Origin', 'Dest', 'UniqueCarrier'), y = 'IsDepDelayed', family = 'binomial', training_frame = airlines.hex)

#Print deviances make sure we're returning a number
Log.info("Print model statistics for R and H2O... \n")
Log.info(paste("H2O Deviance  : ", fitH2O@model$training_metrics@metrics$residual_deviance))
Log.info(paste("H2O Null Dev  : ", fitH2O@model$training_metrics@metrics$null_deviance))
expect_false(fitH2O@model$training_metrics@metrics$null_deviance=="NaN")

}

doTest("GLM Null Dev is numeric", test.glmn1NullDev.golden)

