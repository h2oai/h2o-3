setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdocasfactor.golden <- function(H2Oserver) {

#Example from as.factor R example

prosPath <- system.file("extdata", "prostate.csv", package="h2o")
prostate.hex <- h2o.uploadFile(path = prosPath)
prostate.hex[,4] <- as.factor(prostate.hex[,4])
sum <- summary(prostate.hex[,4])
is <- is.factor(prostate.hex[,4])
print(is)

Log.info("Print output from as.data.frame call")
Log.info(paste("H2O Summary  :" ,sum))

expect_true(is)


testEnd()
}

doTest("R Doc as factor", test.rdocasfactor.golden)

