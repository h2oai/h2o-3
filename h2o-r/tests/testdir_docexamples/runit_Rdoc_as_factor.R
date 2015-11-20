


test.rdocasfactor.golden <- function() {

#Example from as.factor R example

prosPath <- locate("smalldata/extdata/prostate.csv")
prostate.hex <- h2o.uploadFile(path = prosPath)
prostate.hex[,4] <- as.factor(prostate.hex[,4])
sum <- summary(prostate.hex[,4])
is <- is.factor(prostate.hex[,4])
print(is)

Log.info("Print output from as.data.frame call")
Log.info(paste("H2O Summary  :" ,sum))

expect_true(is)



}

doTest("R Doc as factor", test.rdocasfactor.golden)

