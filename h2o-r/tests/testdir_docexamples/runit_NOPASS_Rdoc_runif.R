
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdocrunif.golden <- function(H2Oserver) {

prosPath = system.file("extdata", "prostate.csv", package="h2o")
prostate.hex = h2o.importFile(H2Oserver, path = prosPath, key = "prostate.hex")
s = runif(nrow(prostate.hex))
summary(s)
prostate.train = prostate.hex[s <= 0.8,]
prostate.train = h2o.assign(prostate.train, "prostate.train")
prostate.test = prostate.hex[s > 0.8,]
prostate.test = h2o.assign(prostate.test, "prostate.test")
nrow(prostate.train) + nrow(prostate.test)
count<- nrow(prostate.train) + nrow(prostate.test)
sum<- summary(prostate.test)

Log.info("Print output from as.data.frame call")
Log.info(paste("H2O Count  :" ,count))
Log.info(paste("H2O Summary of HO Set  : " , sum))

testEnd()
}

doTest("R Doc Runif", test.rdocrunif.golden)

