setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../findNSourceUtils.R')

test.glmn1NullDev.golden <- function(H2Oserver) {

airlines.hex =  h2o.importURL(H2Oserver, path = "https://raw.github.com/0xdata/h2o/master/smalldata/airlines/AirlinesTrain.csv.zip")
fitH2O<- h2o.glm(x = c('Distance', 'Origin', 'Dest', 'UniqueCarrier'), y = 'IsDepDelayed', family = 'binomial', data = airlines.hex)

#Print deviances make sure we're returning a number
Log.info("Print model statistics for R and H2O... \n")
Log.info(paste("H2O Deviance  : ", fitH2O@model$deviance))
Log.info(paste("H2O Null Dev  : ", fitH2O@model$null.deviance))
expect_false(fitH2O@model$null.deviance=="NaN")

testEnd()
}

doTest("GLM Null Dev is numeric", test.glmn1NullDev.golden)

